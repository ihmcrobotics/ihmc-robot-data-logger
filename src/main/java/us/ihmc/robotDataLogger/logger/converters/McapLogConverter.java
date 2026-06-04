package us.ihmc.robotDataLogger.logger.converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.robotDataLogger.logger.LogPropertiesReader;
import us.ihmc.robotDataLogger.logger.YoVariableLogReader;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerListener;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

/**
 * Converts an IHMC robot data log directory into a single MCAP file that can be opened
 * in Foxglove Studio (https://foxglove.dev).
 * <p>
 * Channel layout:
 * <ul>
 *   <li>One channel per YoRegistry that contains variables, topic = full registry path.</li>
 *   <li>One channel per joint, topic = {@code /joints/<jointName>}.</li>
 * </ul>
 * All messages use JSON encoding with a JSON Schema declaration so Foxglove can
 * display and plot fields without any additional configuration.
 * <p>
 * The output file is non-indexed (no chunk records), so Foxglove will scan from the
 * beginning when seeking. For large logs this is acceptable; indexed support can be
 * added later on top of this same structure.
 *
 * @see McapWriter
 */
public class McapLogConverter extends YoVariableLogReader
{
   public McapLogConverter(File logDirectory, LogProperties logProperties)
   {
      super(logDirectory, logProperties);
   }

   /**
    * Reads the log in {@code logDirectory} and writes a {@code .mcap} file to {@code outputFile}.
    */
   public void convert(File outputFile) throws IOException
   {
      if (!initialize())
         throw new IOException("Failed to initialize log reader for " + logDirectory);

      YoVariableHandshakeParser parser = ConverterUtil.getHandshake(logProperties.getVariables().getHandshakeFileType(), handshake);

      List<YoVariable> variables    = parser.getYoVariablesList();
      List<JointState> jointStates  = parser.getJointStates();
      int numberOfVariables         = parser.getNumberOfVariables();
      int numberOfJointStateVars    = parser.getNumberOfJointStateVariables();

      // Group variables by their parent registry (insertion order = buffer order).
      LinkedHashMap<YoRegistry, List<Integer>> registryToVarIndices = new LinkedHashMap<>();
      for (int i = 0; i < variables.size(); i++)
      {
         YoRegistry reg = variables.get(i).getRegistry();
         registryToVarIndices.computeIfAbsent(reg, k -> new ArrayList<>()).add(i);
      }

      try (McapWriter mcap = new McapWriter(new FileOutputStream(outputFile)))
      {
         int nextId = 1;

         // Register one schema + channel per registry.
         Map<YoRegistry, Integer> registryChannelIds = new LinkedHashMap<>();
         for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
         {
            YoRegistry       registry   = entry.getKey();
            List<Integer>    varIndices = entry.getValue();
            int              channelId  = nextId++;
            byte[]           schema     = buildRegistrySchema(registry, varIndices, variables);

            mcap.addSchema(channelId, registry.getName(), "jsonschema", schema);
            mcap.addChannel(channelId, channelId, registryTopic(registry), "json", Collections.emptyMap());
            registryChannelIds.put(registry, channelId);
         }

         // Register one schema + channel per joint.
         Map<JointState, Integer> jointChannelIds = new LinkedHashMap<>();
         for (JointState joint : jointStates)
         {
            int    channelId = nextId++;
            byte[] schema    = buildJointSchema(joint);

            mcap.addSchema(channelId, joint.getName(), "jsonschema", schema);
            mcap.addChannel(channelId, channelId, "/joints/" + joint.getName(), "json", Collections.emptyMap());
            jointChannelIds.put(joint, channelId);
         }

         // Iterate all compressed batches and write one MCAP message per channel per tick.
         int batchSize = getBatchSize();
         int numBatches = getNumberOfEntries();
         System.out.printf("Converting %d batches (%d ticks/batch) → %s%n", numBatches, batchSize, outputFile.getName());

         for (int batchIdx = 0; batchIdx < numBatches; batchIdx++)
         {
            if (batchIdx % 1000 == 0)
               System.out.printf("  %.0f%%\r", 100.0 * batchIdx / numBatches);

            ByteBuffer batchData = readData(batchIdx);

            for (int tickIdx = 0; tickIdx < batchSize; tickIdx++)
            {
               if (batchData.remaining() < (1 + numberOfVariables + numberOfJointStateVars) * 8)
                  break;

               long timestamp = batchData.getLong();
               if (timestamp == 0)
                  break; // unfilled slot in a partial last batch

               // Read and apply YoVariable values.
               for (int i = 0; i < numberOfVariables; i++)
                  variables.get(i).setValueFromLongBits(batchData.getLong());

               // Read all joint state longs into a flat array for decoding below.
               long[] jointValues = new long[numberOfJointStateVars];
               for (int i = 0; i < numberOfJointStateVars; i++)
                  jointValues[i] = batchData.getLong();

               // Write one JSON message per registry channel.
               for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
               {
                  byte[] msgBytes = buildRegistryMessage(entry.getValue(), variables);
                  mcap.writeMessage(registryChannelIds.get(entry.getKey()), timestamp, timestamp, msgBytes);
               }

               // Write one JSON message per joint channel.
               int jointOffset = 0;
               for (JointState joint : jointStates)
               {
                  byte[] msgBytes = buildJointMessage(joint, jointValues, jointOffset);
                  mcap.writeMessage(jointChannelIds.get(joint), timestamp, timestamp, msgBytes);
                  jointOffset += joint.getNumberOfStateVariables();
               }
            }
         }
         System.out.println("  100%");
         System.out.println("Done. Output: " + outputFile.getAbsolutePath());
      }
   }

   // ── Schema builders ──────────────────────────────────────────────────────────

   private static byte[] buildRegistrySchema(YoRegistry registry, List<Integer> varIndices, List<YoVariable> variables)
   {
      StringBuilder sb = new StringBuilder("{\"title\":\"")
            .append(escapeJson(registry.getName()))
            .append("\",\"type\":\"object\",\"properties\":{");
      boolean first = true;
      for (int idx : varIndices)
      {
         if (!first) sb.append(',');
         YoVariable v = variables.get(idx);
         sb.append('"').append(escapeJson(v.getName())).append("\":").append(jsonSchemaType(v));
         first = false;
      }
      sb.append("}}");
      return sb.toString().getBytes(StandardCharsets.UTF_8);
   }

   private static String jsonSchemaType(YoVariable v)
   {
      if (v instanceof YoDouble)  return "{\"type\":\"number\"}";
      if (v instanceof YoBoolean) return "{\"type\":\"boolean\"}";
      if (v instanceof YoInteger) return "{\"type\":\"integer\"}";
      if (v instanceof YoLong)    return "{\"type\":\"integer\"}";
      if (v instanceof YoEnum)    return "{\"type\":\"string\"}";
      return "{\"type\":\"string\"}";
   }

   private static byte[] buildJointSchema(JointState joint)
   {
      String json;
      if (joint instanceof OneDoFState)
      {
         json = "{\"title\":\"" + escapeJson(joint.getName()) + "\","
               + "\"type\":\"object\","
               + "\"properties\":{"
               + "\"q\":{\"type\":\"number\",\"description\":\"Position (rad)\"},"
               + "\"qd\":{\"type\":\"number\",\"description\":\"Velocity (rad/s)\"}}}";
      }
      else
      {
         // SixDoFState layout in buffer: qs,qx,qy,qz | tx,ty,tz | angX,angY,angZ | linX,linY,linZ
         json = "{\"title\":\"" + escapeJson(joint.getName()) + "\","
               + "\"type\":\"object\","
               + "\"properties\":{"
               + "\"rotation\":{\"type\":\"object\",\"properties\":{\"s\":{\"type\":\"number\"},\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"}}},"
               + "\"translation\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"}}},"
               + "\"angularTwist\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"}}},"
               + "\"linearTwist\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"}}}"
               + "}}";
      }
      return json.getBytes(StandardCharsets.UTF_8);
   }

   // ── Message builders ─────────────────────────────────────────────────────────

   private static byte[] buildRegistryMessage(List<Integer> varIndices, List<YoVariable> variables)
   {
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (int idx : varIndices)
      {
         if (!first) sb.append(',');
         YoVariable v = variables.get(idx);
         sb.append('"').append(escapeJson(v.getName())).append("\":").append(jsonValue(v));
         first = false;
      }
      sb.append('}');
      return sb.toString().getBytes(StandardCharsets.UTF_8);
   }

   private static String jsonValue(YoVariable v)
   {
      if (v instanceof YoDouble)
      {
         double d = ((YoDouble) v).getValue();
         if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
         return Double.toString(d);
      }
      if (v instanceof YoBoolean) return Boolean.toString(((YoBoolean) v).getValue());
      if (v instanceof YoInteger) return Integer.toString(((YoInteger) v).getValue());
      if (v instanceof YoLong)    return Long.toString(((YoLong) v).getValue());
      if (v instanceof YoEnum)
      {
         String s = v.getValueAsString();
         if (s == null) return "null";
         return "\"" + escapeJson(s) + "\"";
      }
      return "null";
   }

   private static byte[] buildJointMessage(JointState joint, long[] jointValues, int offset)
   {
      StringBuilder sb = new StringBuilder("{");
      if (joint instanceof OneDoFState)
      {
         double q  = Double.longBitsToDouble(jointValues[offset]);
         double qd = Double.longBitsToDouble(jointValues[offset + 1]);
         sb.append("\"q\":").append(doubleOrNull(q))
           .append(",\"qd\":").append(doubleOrNull(qd));
      }
      else
      {
         // SixDoFState: 13 values (see SixDoFState.update)
         double qs   = Double.longBitsToDouble(jointValues[offset]);
         double qx   = Double.longBitsToDouble(jointValues[offset + 1]);
         double qy   = Double.longBitsToDouble(jointValues[offset + 2]);
         double qz   = Double.longBitsToDouble(jointValues[offset + 3]);
         double tx   = Double.longBitsToDouble(jointValues[offset + 4]);
         double ty   = Double.longBitsToDouble(jointValues[offset + 5]);
         double tz   = Double.longBitsToDouble(jointValues[offset + 6]);
         double angX = Double.longBitsToDouble(jointValues[offset + 7]);
         double angY = Double.longBitsToDouble(jointValues[offset + 8]);
         double angZ = Double.longBitsToDouble(jointValues[offset + 9]);
         double linX = Double.longBitsToDouble(jointValues[offset + 10]);
         double linY = Double.longBitsToDouble(jointValues[offset + 11]);
         double linZ = Double.longBitsToDouble(jointValues[offset + 12]);

         sb.append("\"rotation\":{\"s\":").append(doubleOrNull(qs))
           .append(",\"x\":").append(doubleOrNull(qx))
           .append(",\"y\":").append(doubleOrNull(qy))
           .append(",\"z\":").append(doubleOrNull(qz)).append('}');
         sb.append(",\"translation\":{\"x\":").append(doubleOrNull(tx))
           .append(",\"y\":").append(doubleOrNull(ty))
           .append(",\"z\":").append(doubleOrNull(tz)).append('}');
         sb.append(",\"angularTwist\":{\"x\":").append(doubleOrNull(angX))
           .append(",\"y\":").append(doubleOrNull(angY))
           .append(",\"z\":").append(doubleOrNull(angZ)).append('}');
         sb.append(",\"linearTwist\":{\"x\":").append(doubleOrNull(linX))
           .append(",\"y\":").append(doubleOrNull(linY))
           .append(",\"z\":").append(doubleOrNull(linZ)).append('}');
      }
      sb.append('}');
      return sb.toString().getBytes(StandardCharsets.UTF_8);
   }

   // ── Utilities ────────────────────────────────────────────────────────────────

   private static String registryTopic(YoRegistry registry)
   {
      Deque<String> parts = new ArrayDeque<>();
      YoRegistry current = registry;
      while (current != null)
      {
         parts.addFirst(current.getName());
         current = current.getParent();
      }
      return "/" + String.join("/", parts);
   }

   private static String doubleOrNull(double d)
   {
      return (Double.isNaN(d) || Double.isInfinite(d)) ? "null" : Double.toString(d);
   }

   private static String escapeJson(String s)
   {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   // ── Entry point ───────────────────────────────────────────────────────────────

   // Set this to the log directory you want to convert, then run main().
   private static final String LOG_DIRECTORY = "/Users/wayne/Documents/20260529_152027_WalkingAttemptTwoStepsFailure/p";

   public static void main(String[] args) throws IOException
   {
      File logDirectory = new File(LOG_DIRECTORY);
      if (!logDirectory.isDirectory())
         throw new IllegalArgumentException("Not a directory: " + logDirectory.getAbsolutePath());

      File outputFile = new File(logDirectory, logDirectory.getName() + ".mcap");

      File propertyFile = new File(logDirectory, YoVariableLoggerListener.propertyFile);
      LogProperties properties = new LogPropertiesReader(propertyFile);

      McapLogConverter converter = new McapLogConverter(logDirectory, properties);
      converter.convert(outputFile);
      converter.close();
   }
}
