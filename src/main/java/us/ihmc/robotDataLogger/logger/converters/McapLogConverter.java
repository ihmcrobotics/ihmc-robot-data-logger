package us.ihmc.robotDataLogger.logger.converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
 * All messages use CDR binary encoding with ros2msg schema declarations so Foxglove can
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
            String           topic      = registryTopic(registry);
            byte[]           schema     = buildRegistrySchemaRos2(varIndices, variables);

            mcap.addSchema(channelId, topic, "ros2msg", schema);
            mcap.addChannel(channelId, channelId, topic, "cdr", Collections.emptyMap());
            registryChannelIds.put(registry, channelId);
         }

         // Register one schema + channel per joint.
         Map<JointState, Integer> jointChannelIds = new LinkedHashMap<>();
         for (JointState joint : jointStates)
         {
            int    channelId = nextId++;
            String topic     = "/joints/" + joint.getName();
            byte[] schema    = buildJointSchemaRos2(joint);

            mcap.addSchema(channelId, topic, "ros2msg", schema);
            mcap.addChannel(channelId, channelId, topic, "cdr", Collections.emptyMap());
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

               // Write one CDR message per registry channel.
               for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
               {
                  byte[] msgBytes = buildRegistryCdrMessage(entry.getValue(), variables);
                  mcap.writeMessage(registryChannelIds.get(entry.getKey()), timestamp, timestamp, msgBytes);
               }

               // Write one CDR message per joint channel.
               int jointOffset = 0;
               for (JointState joint : jointStates)
               {
                  byte[] msgBytes = buildJointCdrMessage(joint, jointValues, jointOffset);
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

   private static byte[] buildRegistrySchemaRos2(List<Integer> varIndices, List<YoVariable> variables)
   {
      StringBuilder sb = new StringBuilder();
      for (int idx : varIndices)
      {
         YoVariable v = variables.get(idx);
         sb.append(ros2MsgType(v)).append(' ').append(toSnakeCase(v.getName())).append('\n');
      }
      return sb.toString().getBytes(StandardCharsets.UTF_8);
   }

   private static String toSnakeCase(String name)
   {
      String s = name
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")    // fooBar → foo_Bar
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2") // XMLParser → XML_Parser
            .toLowerCase()
            .replaceAll("_+", "_");                        // collapse any consecutive underscores
      while (s.startsWith("_")) s = s.substring(1);
      while (s.endsWith("_"))   s = s.substring(0, s.length() - 1);
      if (!s.isEmpty() && Character.isDigit(s.charAt(0)))
         s = "f_" + s;
      return s;
   }

   private static String ros2MsgType(YoVariable v)
   {
      if (v instanceof YoDouble)  return "float64";
      if (v instanceof YoBoolean) return "bool";
      if (v instanceof YoInteger) return "int32";
      if (v instanceof YoLong)    return "int64";
      return "string"; // YoEnum and unknown types
   }

   private static byte[] buildJointSchemaRos2(JointState joint)
   {
      String schema;
      if (joint instanceof OneDoFState)
      {
         schema = "float64 q\nfloat64 qd\n";
      }
      else
      {
         // SixDoFState layout in buffer: qs,qx,qy,qz | tx,ty,tz | angX,angY,angZ | linX,linY,linZ
         schema = "float64 rotation_s\nfloat64 rotation_x\nfloat64 rotation_y\nfloat64 rotation_z\n"
               + "float64 translation_x\nfloat64 translation_y\nfloat64 translation_z\n"
               + "float64 angular_twist_x\nfloat64 angular_twist_y\nfloat64 angular_twist_z\n"
               + "float64 linear_twist_x\nfloat64 linear_twist_y\nfloat64 linear_twist_z\n";
      }
      return schema.getBytes(StandardCharsets.UTF_8);
   }

   // ── Message builders ─────────────────────────────────────────────────────────

   private static byte[] buildRegistryCdrMessage(List<Integer> varIndices, List<YoVariable> variables)
   {
      // Upper bound: 4-byte header + per variable: 8 alignment + 8 data (or 269 for enum strings)
      int maxSize = 4;
      for (int idx : varIndices)
         maxSize += (variables.get(idx) instanceof YoEnum) ? 269 : 15;

      ByteBuffer buf = ByteBuffer.allocate(maxSize).order(ByteOrder.LITTLE_ENDIAN);
      buf.put((byte) 0x00); buf.put((byte) 0x01); buf.put((byte) 0x00); buf.put((byte) 0x00);

      for (int idx : varIndices)
      {
         YoVariable v = variables.get(idx);
         if (v instanceof YoDouble)
         {
            cdrAlign(buf, 8);
            buf.putDouble(((YoDouble) v).getValue());
         }
         else if (v instanceof YoBoolean)
         {
            buf.put(((YoBoolean) v).getValue() ? (byte) 1 : (byte) 0);
         }
         else if (v instanceof YoInteger)
         {
            cdrAlign(buf, 4);
            buf.putInt(((YoInteger) v).getValue());
         }
         else if (v instanceof YoLong)
         {
            cdrAlign(buf, 8);
            buf.putLong(((YoLong) v).getValue());
         }
         else if (v instanceof YoEnum)
         {
            cdrAlign(buf, 4);
            String s = v.getValueAsString();
            byte[] strBytes = (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
            buf.putInt(strBytes.length + 1); // CDR string length includes null terminator
            buf.put(strBytes);
            buf.put((byte) 0);
         }
      }

      return Arrays.copyOf(buf.array(), buf.position());
   }

   private static byte[] buildJointCdrMessage(JointState joint, long[] jointValues, int offset)
   {
      int numFields = joint.getNumberOfStateVariables();
      // 4-byte header + numFields float64s (all naturally 8-byte aligned from payload start)
      ByteBuffer buf = ByteBuffer.allocate(4 + numFields * 8).order(ByteOrder.LITTLE_ENDIAN);
      buf.put((byte) 0x00); buf.put((byte) 0x01); buf.put((byte) 0x00); buf.put((byte) 0x00);
      for (int i = 0; i < numFields; i++)
         buf.putDouble(Double.longBitsToDouble(jointValues[offset + i]));
      return buf.array();
   }

   // ── Utilities ────────────────────────────────────────────────────────────────

   private static void cdrAlign(ByteBuffer buf, int alignment)
   {
      int rem = (buf.position() - 4) % alignment; // CDR aligns from payload start, not buffer start
      if (rem != 0)
      {
         int pad = alignment - rem;
         for (int i = 0; i < pad; i++) buf.put((byte) 0);
      }
   }

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

   // ── Entry point ───────────────────────────────────────────────────────────────

   // Set this to the log directory you want to convert, then run main().
   private static final String LOG_DIRECTORY = "/opt/ihmc/LogData/_Issues/20260626_104940_Alex002_AutomaticEstopFromFortRobotics/";

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
