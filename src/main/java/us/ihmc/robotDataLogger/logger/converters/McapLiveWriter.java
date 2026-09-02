package us.ihmc.robotDataLogger.logger.converters;

import java.io.Closeable;
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

import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

/**
 * Writes live robot data directly to an MCAP file with CDR-encoded messages and ros2msg schemas.
 * Intended as a drop-in replacement for the bsz/dat file pair in YoVariableLoggerListener.
 * <p>
 * Thread safety: {@link #writeBatch} is meant to be called only from the background compression
 * thread. {@link #reset} and {@link #close} must only be called after that thread has stopped.
 */
public class McapLiveWriter implements Closeable
{
   public static final String MCAP_FILENAME = "robotData.mcap";

   private McapWriter mcapWriter;
   private final File logFile;

   private final List<YoVariable> variables;
   private final List<JointState> jointStates;
   private final int numberOfVariables;
   private final int numberOfJointStateVars;
   private final long[] jointValuesScratch;

   private final LinkedHashMap<YoRegistry, List<Integer>> registryToVarIndices = new LinkedHashMap<>();
   private final LinkedHashMap<YoRegistry, Integer> registryChannelIds = new LinkedHashMap<>();
   private final LinkedHashMap<JointState, Integer> jointChannelIds = new LinkedHashMap<>();

   public McapLiveWriter(File logDirectory, YoVariableHandshakeParser parser) throws IOException
   {
      logFile = new File(logDirectory, MCAP_FILENAME);
      variables = parser.getYoVariablesList();
      jointStates = parser.getJointStates();
      numberOfVariables = parser.getNumberOfVariables();
      numberOfJointStateVars = parser.getNumberOfJointStateVariables();
      jointValuesScratch = new long[numberOfJointStateVars];

      for (int i = 0; i < variables.size(); i++)
      {
         YoRegistry reg = variables.get(i).getRegistry();
         registryToVarIndices.computeIfAbsent(reg, k -> new ArrayList<>()).add(i);
      }

      openAndRegister();
   }

   private void openAndRegister() throws IOException
   {
      mcapWriter = new McapWriter(new FileOutputStream(logFile, false));
      int nextId = 1;

      for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
      {
         int channelId = nextId++;
         String topic = registryTopic(entry.getKey());
         mcapWriter.addSchema(channelId, topic, "ros2msg", buildRegistrySchemaRos2(entry.getValue()));
         mcapWriter.addChannel(channelId, channelId, topic, "cdr", Collections.emptyMap());
         registryChannelIds.put(entry.getKey(), channelId);
      }

      for (JointState joint : jointStates)
      {
         int channelId = nextId++;
         String topic = "/joints/" + joint.getName();
         mcapWriter.addSchema(channelId, topic, "ros2msg", buildJointSchemaRos2(joint));
         mcapWriter.addChannel(channelId, channelId, topic, "cdr", Collections.emptyMap());
         jointChannelIds.put(joint, channelId);
      }
   }

   /**
    * Decodes all ticks from {@code batch} (starting at its current position) and writes one MCAP
    * message per channel per tick. Stops early on a zero timestamp (unfilled slot in a partial batch).
    */
   public void writeBatch(ByteBuffer batch) throws IOException
   {
      int tickBytes = (1 + numberOfVariables + numberOfJointStateVars) * Long.BYTES;
      while (batch.remaining() >= tickBytes)
      {
         long timestamp = batch.getLong();
         if (timestamp == 0)
            break;

         for (int i = 0; i < numberOfVariables; i++)
            variables.get(i).setValueFromLongBits(batch.getLong());
         for (int i = 0; i < numberOfJointStateVars; i++)
            jointValuesScratch[i] = batch.getLong();

         for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
            mcapWriter.writeMessage(registryChannelIds.get(entry.getKey()), timestamp, timestamp, buildRegistryCdrMessage(entry.getValue()));

         int jointOffset = 0;
         for (JointState joint : jointStates)
         {
            mcapWriter.writeMessage(jointChannelIds.get(joint), timestamp, timestamp, buildJointCdrMessage(joint, jointOffset));
            jointOffset += joint.getNumberOfStateVariables();
         }
      }
   }

   /** Close the current file and open a fresh one at the same path (for CLEAR_LOG). */
   public void reset() throws IOException
   {
      mcapWriter.close();
      registryChannelIds.clear();
      jointChannelIds.clear();
      openAndRegister();
   }

   public File getLogFile()
   {
      return logFile;
   }

   @Override
   public void close() throws IOException
   {
      mcapWriter.close();
   }

   // ── Schema builders ──────────────────────────────────────────────────────────

   private byte[] buildRegistrySchemaRos2(List<Integer> varIndices)
   {
      StringBuilder sb = new StringBuilder();
      for (int idx : varIndices)
      {
         YoVariable v = variables.get(idx);
         sb.append(ros2MsgType(v)).append(' ').append(toSnakeCase(v.getName())).append('\n');
      }
      return sb.toString().getBytes(StandardCharsets.UTF_8);
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
         schema = "float64 rotation_s\nfloat64 rotation_x\nfloat64 rotation_y\nfloat64 rotation_z\n"
               + "float64 translation_x\nfloat64 translation_y\nfloat64 translation_z\n"
               + "float64 angular_twist_x\nfloat64 angular_twist_y\nfloat64 angular_twist_z\n"
               + "float64 linear_twist_x\nfloat64 linear_twist_y\nfloat64 linear_twist_z\n";
      }
      return schema.getBytes(StandardCharsets.UTF_8);
   }

   // ── Message builders ─────────────────────────────────────────────────────────

   private byte[] buildRegistryCdrMessage(List<Integer> varIndices)
   {
      int maxSize = 4;
      for (int idx : varIndices)
         maxSize += (variables.get(idx) instanceof YoEnum) ? 269 : 15;

      ByteBuffer buf = ByteBuffer.allocate(maxSize).order(ByteOrder.LITTLE_ENDIAN);
      buf.put((byte) 0x00);
      buf.put((byte) 0x01);
      buf.put((byte) 0x00);
      buf.put((byte) 0x00);

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
            buf.putInt(strBytes.length + 1);
            buf.put(strBytes);
            buf.put((byte) 0);
         }
      }
      return Arrays.copyOf(buf.array(), buf.position());
   }

   private byte[] buildJointCdrMessage(JointState joint, int offset)
   {
      int numFields = joint.getNumberOfStateVariables();
      ByteBuffer buf = ByteBuffer.allocate(4 + 4 + numFields * 8).order(ByteOrder.LITTLE_ENDIAN);
      buf.put((byte) 0x00);
      buf.put((byte) 0x01);
      buf.put((byte) 0x00);
      buf.put((byte) 0x00);
      cdrAlign(buf, 8);
      for (int i = 0; i < numFields; i++)
         buf.putDouble(Double.longBitsToDouble(jointValuesScratch[offset + i]));
      return buf.array();
   }

   // ── Utilities ────────────────────────────────────────────────────────────────

   private static void cdrAlign(ByteBuffer buf, int alignment)
   {
      int rem = buf.position() % alignment;
      if (rem != 0)
      {
         int pad = alignment - rem;
         for (int i = 0; i < pad; i++)
            buf.put((byte) 0);
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

   private static String toSnakeCase(String name)
   {
      String s = name
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .toLowerCase()
            .replaceAll("_+", "_");
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
      return "string";
   }
}
