package us.ihmc.robotDataLogger.logger.converters;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import logger_msgs.LogProperties;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.mecano.tools.MultiBodySystemTools;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.robotDataLogger.jointState.SixDoFState;
import us.ihmc.robotDataLogger.logger.LogPropertiesReader;
import us.ihmc.robotDataLogger.logger.YoVariableLogReader;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerListener;
import us.ihmc.scs2.definition.robot.RobotDefinition;
import us.ihmc.scs2.definition.robot.urdf.URDFTools;
import us.ihmc.scs2.definition.robot.urdf.items.URDFModel;
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
 *   <li>{@code /robot_description} — URDF content as a latched std_msgs/String for Foxglove's 3D panel.</li>
 *   <li>{@code /joint_states} — sensor_msgs/JointState with all OneDoF joints per tick.</li>
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

      // Read the URDF model if present (model.sdf is actually URDF format).
      File modelFile = new File(logDirectory, logProperties.getModel().getPathAsString());
      String urdfContent = null;
      if (modelFile.exists())
      {
         String raw = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
         // Extract meshes into the same directory as the output MCAP file.
         File resourcesZip = new File(logDirectory, logProperties.getModel().getResourceBundleAsString());
         File meshDestDir  = outputFile.getParentFile();
         if (resourcesZip.exists())
            extractZipIfNeeded(resourcesZip, meshDestDir);
         urdfContent = stripNonUrdfExtensions(rewriteMeshPaths(raw));

         // Write the cleaned URDF as a standalone file so Foxglove can load it via URL source.
         File urdfFile = new File(meshDestDir, "robot.urdf");
         Files.write(urdfFile.toPath(), urdfContent.getBytes(StandardCharsets.UTF_8));

         System.out.println("Robot model ready. To load in Foxglove:");
         System.out.println("  1. Run in a terminal:  python3 -m http.server " + HTTP_PORT + " --directory \"" + meshDestDir.getAbsolutePath() + "\"");
         System.out.println("  2. Foxglove 3D panel → Custom layers → URDF → Source: URL → http://localhost:" + HTTP_PORT + "/robot.urdf");
      }

      // Precompute OneDoF joints and the SixDoF (floating-base) joint with their jointValues offsets.
      List<JointState> oneDoFJoints    = new ArrayList<>();
      List<byte[]>     oneDoFNameBytes = new ArrayList<>();
      int[]            oneDoFOffsets;
      SixDoFState      sixDoFJoint     = null;
      int              sixDoFOffset    = -1;
      {
         List<Integer> offsets = new ArrayList<>();
         int cumOffset = 0;
         for (JointState joint : jointStates)
         {
            if (joint instanceof OneDoFState)
            {
               oneDoFJoints.add(joint);
               oneDoFNameBytes.add(joint.getName().getBytes(StandardCharsets.UTF_8));
               offsets.add(cumOffset);
            }
            else if (joint instanceof SixDoFState && sixDoFJoint == null)
            {
               sixDoFJoint  = (SixDoFState) joint;
               sixDoFOffset = cumOffset;
            }
            cumOffset += joint.getNumberOfStateVariables();
         }
         oneDoFOffsets = offsets.stream().mapToInt(Integer::intValue).toArray();
      }

      // Root link name from URDF — used as the TF child frame so Foxglove anchors the model.
      String urdfRootLink = urdfContent != null ? findUrdfRootLink(urdfContent) : null;

      // Kinematic tree used to compute a parent-link -> child-link transform for every joint (not just the
      // floating pelvis), so consumers that build a full TF tree (e.g. SCS2) can animate the whole robot.
      MecanoKinematics mecanoKinematics = urdfContent != null && urdfRootLink != null
            ? buildMecanoKinematics(urdfContent, urdfRootLink, oneDoFJoints)
            : null;

      try (McapWriter mcap = new McapWriter(new FileOutputStream(outputFile)))
      {
         int nextId = 1;

         // Register /robot_description channel (std_msgs/String).
         int robotDescriptionChannelId = -1;
         if (urdfContent != null)
         {
            robotDescriptionChannelId = nextId++;
            mcap.addSchema(robotDescriptionChannelId, "std_msgs/String", "ros2msg", "string data\n".getBytes(StandardCharsets.UTF_8));
            mcap.addChannel(robotDescriptionChannelId, robotDescriptionChannelId, "/robot_description", "cdr", Collections.emptyMap());
         }

         // Register /joint_states channel (sensor_msgs/JointState).
         int jointStatesChannelId = nextId++;
         mcap.addSchema(jointStatesChannelId, "sensor_msgs/msg/JointState", "ros2msg", JOINT_STATE_SCHEMA.getBytes(StandardCharsets.UTF_8));
         mcap.addChannel(jointStatesChannelId, jointStatesChannelId, "/joint_states", "cdr", Collections.emptyMap());

         // Register /tf channel (tf2_msgs/TFMessage) for the floating-base pelvis pose.
         int tfChannelId = -1;
         if (sixDoFJoint != null && urdfRootLink != null)
         {
            tfChannelId = nextId++;
            mcap.addSchema(tfChannelId, "tf2_msgs/msg/TFMessage", "ros2msg", TF_SCHEMA.getBytes(StandardCharsets.UTF_8));
            mcap.addChannel(tfChannelId, tfChannelId, "/tf", "cdr", Collections.emptyMap());
         }

         // Register /odom channel (nav_msgs/Odometry) with the floating-base pelvis's exact pose AND twist -
         // unlike /tf, this is a standard message type that carries velocity, so consumers don't have to
         // approximate it (e.g. by finite-differencing /tf poses).
         int odomChannelId = -1;
         if (sixDoFJoint != null && urdfRootLink != null)
         {
            odomChannelId = nextId++;
            mcap.addSchema(odomChannelId, "nav_msgs/msg/Odometry", "ros2msg", ODOMETRY_SCHEMA.getBytes(StandardCharsets.UTF_8));
            mcap.addChannel(odomChannelId, odomChannelId, "/odom", "cdr", Collections.emptyMap());
         }

         // Register one schema + channel per registry.
         Map<YoRegistry, Integer> registryChannelIds = new LinkedHashMap<>();
         for (Map.Entry<YoRegistry, List<Integer>> entry : registryToVarIndices.entrySet())
         {
            YoRegistry       registry   = entry.getKey();
            List<Integer>    varIndices = entry.getValue();
            int              channelId  = nextId++;
            String           topic      = registryTopic(registry);
            byte[]           schema     = buildRegistrySchemaRos2(varIndices, variables);

            mcap.addSchema(channelId, schemaResourceName(topic), "ros2msg", schema);
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

            mcap.addSchema(channelId, schemaResourceName(topic), "ros2msg", schema);
            mcap.addChannel(channelId, channelId, topic, "cdr", Collections.emptyMap());
            jointChannelIds.put(joint, channelId);
         }

         // Iterate all compressed batches and write one MCAP message per channel per tick.
         int     batchSize  = getBatchSize();
         int     numBatches = getNumberOfEntries();
         boolean firstTick  = true;
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

               // Write /robot_description once at the first tick's timestamp.
               if (firstTick)
               {
                  firstTick = false;
                  if (urdfContent != null)
                     mcap.writeMessage(robotDescriptionChannelId, timestamp, timestamp, buildRobotDescriptionMessage(urdfContent));
               }

               // Write /joint_states.
               mcap.writeMessage(jointStatesChannelId, timestamp, timestamp,
                                 buildJointStateCdrMessage(oneDoFNameBytes, oneDoFOffsets, jointValues, timestamp));

               // Write /tf: the floating-base (pelvis) pose in the world frame, plus a parent->child
               // transform for every other joint in the robot (computed via the URDF kinematic tree).
               // SixDoFState layout: qs,qx,qy,qz | tx,ty,tz | ...
               if (tfChannelId >= 0)
               {
                  double tx = Double.longBitsToDouble(jointValues[sixDoFOffset + 4]);
                  double ty = Double.longBitsToDouble(jointValues[sixDoFOffset + 5]);
                  double tz = Double.longBitsToDouble(jointValues[sixDoFOffset + 6]);
                  double rx = Double.longBitsToDouble(jointValues[sixDoFOffset + 1]);
                  double ry = Double.longBitsToDouble(jointValues[sixDoFOffset + 2]);
                  double rz = Double.longBitsToDouble(jointValues[sixDoFOffset + 3]);
                  double rw = Double.longBitsToDouble(jointValues[sixDoFOffset + 0]); // qs = w

                  List<TfEntry> tfEntries = new ArrayList<>();
                  tfEntries.add(new TfEntry("map", urdfRootLink, tx, ty, tz, rx, ry, rz, rw));

                  if (mecanoKinematics != null)
                  {
                     OneDoFJointBasics[] mecanoOneDoFJoints = mecanoKinematics.mecanoOneDoFJoints();
                     for (int i = 0; i < mecanoOneDoFJoints.length; i++)
                     {
                        if (mecanoOneDoFJoints[i] != null)
                           mecanoOneDoFJoints[i].setQ(Double.longBitsToDouble(jointValues[oneDoFOffsets[i]]));
                     }
                     mecanoKinematics.elevator().updateFramesRecursively();

                     for (JointBasics joint : mecanoKinematics.jointsToPublish())
                     {
                        RigidBodyTransformReadOnly transformToParent = joint.getSuccessor()
                                                                             .getBodyFixedFrame()
                                                                             .getTransformToDesiredFrame(joint.getPredecessor().getBodyFixedFrame());
                        Quaternion rotation = new Quaternion(transformToParent.getRotation());
                        tfEntries.add(new TfEntry(joint.getPredecessor().getName(),
                                                  joint.getSuccessor().getName(),
                                                  transformToParent.getTranslation().getX(),
                                                  transformToParent.getTranslation().getY(),
                                                  transformToParent.getTranslation().getZ(),
                                                  rotation.getX(),
                                                  rotation.getY(),
                                                  rotation.getZ(),
                                                  rotation.getS()));
                     }
                  }

                  mcap.writeMessage(tfChannelId, timestamp, timestamp, buildTfMessage(timestamp, tfEntries));
               }

               // Write /odom: the same pelvis pose as /tf, plus its exact twist (offsets 7-12, unused by any
               // other channel) - so consumers get real velocity instead of having to approximate one.
               if (odomChannelId >= 0)
               {
                  double tx = Double.longBitsToDouble(jointValues[sixDoFOffset + 4]);
                  double ty = Double.longBitsToDouble(jointValues[sixDoFOffset + 5]);
                  double tz = Double.longBitsToDouble(jointValues[sixDoFOffset + 6]);
                  double rx = Double.longBitsToDouble(jointValues[sixDoFOffset + 1]);
                  double ry = Double.longBitsToDouble(jointValues[sixDoFOffset + 2]);
                  double rz = Double.longBitsToDouble(jointValues[sixDoFOffset + 3]);
                  double rw = Double.longBitsToDouble(jointValues[sixDoFOffset + 0]); // qs = w
                  double angularX = Double.longBitsToDouble(jointValues[sixDoFOffset + 7]);
                  double angularY = Double.longBitsToDouble(jointValues[sixDoFOffset + 8]);
                  double angularZ = Double.longBitsToDouble(jointValues[sixDoFOffset + 9]);
                  double linearX = Double.longBitsToDouble(jointValues[sixDoFOffset + 10]);
                  double linearY = Double.longBitsToDouble(jointValues[sixDoFOffset + 11]);
                  double linearZ = Double.longBitsToDouble(jointValues[sixDoFOffset + 12]);

                  mcap.writeMessage(odomChannelId,
                                    timestamp,
                                    timestamp,
                                    buildOdometryMessage(timestamp, "map", urdfRootLink, tx, ty, tz, rx, ry, rz, rw, angularX, angularY, angularZ, linearX,
                                                         linearY, linearZ));
               }

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

   // ── Constants ────────────────────────────────────────────────────────────────

   private static final int HTTP_PORT = 8765;

   private static final String TF_SCHEMA =
         "geometry_msgs/TransformStamped[] transforms\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/TransformStamped\n" +
         "std_msgs/Header header\n" +
         "string child_frame_id\n" +
         "geometry_msgs/Transform transform\n" +
         "\n================================================================================\n" +
         "MSG: std_msgs/Header\n" +
         "builtin_interfaces/Time stamp\n" +
         "string frame_id\n" +
         "\n================================================================================\n" +
         "MSG: builtin_interfaces/Time\n" +
         "int32 sec\n" +
         "uint32 nanosec\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Transform\n" +
         "geometry_msgs/Vector3 translation\n" +
         "geometry_msgs/Quaternion rotation\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Vector3\n" +
         "float64 x\n" +
         "float64 y\n" +
         "float64 z\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Quaternion\n" +
         "float64 x\n" +
         "float64 y\n" +
         "float64 z\n" +
         "float64 w\n";

   private static final String JOINT_STATE_SCHEMA =
         "std_msgs/Header header\n" +
         "string[] name\n" +
         "float64[] position\n" +
         "float64[] velocity\n" +
         "float64[] effort\n" +
         "\n================================================================================\n" +
         "MSG: std_msgs/Header\n" +
         "builtin_interfaces/Time stamp\n" +
         "string frame_id\n" +
         "\n================================================================================\n" +
         "MSG: builtin_interfaces/Time\n" +
         "int32 sec\n" +
         "uint32 nanosec\n";

   private static final String ODOMETRY_SCHEMA =
         "std_msgs/Header header\n" +
         "string child_frame_id\n" +
         "geometry_msgs/PoseWithCovariance pose\n" +
         "geometry_msgs/TwistWithCovariance twist\n" +
         "\n================================================================================\n" +
         "MSG: std_msgs/Header\n" +
         "builtin_interfaces/Time stamp\n" +
         "string frame_id\n" +
         "\n================================================================================\n" +
         "MSG: builtin_interfaces/Time\n" +
         "int32 sec\n" +
         "uint32 nanosec\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/PoseWithCovariance\n" +
         "geometry_msgs/Pose pose\n" +
         "float64[36] covariance\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Pose\n" +
         "geometry_msgs/Point position\n" +
         "geometry_msgs/Quaternion orientation\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Point\n" +
         "float64 x\n" +
         "float64 y\n" +
         "float64 z\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Quaternion\n" +
         "float64 x\n" +
         "float64 y\n" +
         "float64 z\n" +
         "float64 w\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/TwistWithCovariance\n" +
         "geometry_msgs/Twist twist\n" +
         "float64[36] covariance\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Twist\n" +
         "geometry_msgs/Vector3 linear\n" +
         "geometry_msgs/Vector3 angular\n" +
         "\n================================================================================\n" +
         "MSG: geometry_msgs/Vector3\n" +
         "float64 x\n" +
         "float64 y\n" +
         "float64 z\n";

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

   /**
    * Serializes a jros2-generated {@link us.ihmc.jros2.ROS2Message} (CDR encoding) the same way
    * {@link us.ihmc.jros2.ROS2Publisher} does internally, so the standard message types below get correct,
    * generator-maintained (de)serialization instead of hand-rolled {@link ByteBuffer} packing.
    * <p>
    * {@code calculateSizeBytes} is used only to presize the buffer (as {@code ROS2Publisher} does) - it is not
    * trusted for the final trim. For an {@code IDLObjectSequence} of variable-size structs (e.g. the
    * {@code TransformStamped[]} in {@code tf2_msgs/TFMessage}), {@code IDLSequence#calculateSizeBytes} passes each
    * element's total byte size as if it were an alignment boundary, which can overestimate the required size.
    * Trimming to the buffer's actual post-serialize position (as the old hand-rolled CDR code always did) avoids
    * shipping that overestimate as trailing garbage bytes in the MCAP message payload.
    */
   private static byte[] serializeRos2Message(CDRSerializable message)
   {
      CDRBuffer buffer = new CDRBuffer();
      int estimatedSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes(0);
      buffer.ensureRemainingCapacity(estimatedSizeBytes);
      buffer.writePayloadHeader();
      message.serialize(buffer);
      return Arrays.copyOf(buffer.getBufferUnsafe().array(), buffer.getBufferUnsafe().position());
   }

   private static byte[] buildTfMessage(long timestampNs, List<TfEntry> entries)
   {
      tf2_msgs.TFMessage message = new tf2_msgs.TFMessage();
      int sec     = (int) (timestampNs / 1_000_000_000L);
      int nanosec = (int) (timestampNs % 1_000_000_000L);

      for (TfEntry entry : entries)
      {
         geometry_msgs.TransformStamped transformStamped = message.getTransforms().add();
         transformStamped.getHeader().getStamp().setSec(sec);
         transformStamped.getHeader().getStamp().setNanosec(nanosec);
         transformStamped.getHeader().setFrameId(entry.parentFrame());
         transformStamped.setChildFrameId(entry.childFrame());

         geometry_msgs.Vector3 translation = transformStamped.getTransform().getTranslation();
         translation.setX(entry.tx());
         translation.setY(entry.ty());
         translation.setZ(entry.tz());

         geometry_msgs.Quaternion rotation = transformStamped.getTransform().getRotation();
         rotation.setX(entry.rx());
         rotation.setY(entry.ry());
         rotation.setZ(entry.rz());
         rotation.setW(entry.rw());
      }

      return serializeRos2Message(message);
   }

   private record TfEntry(String parentFrame, String childFrame, double tx, double ty, double tz, double rx, double ry, double rz, double rw)
   {
   }

   /**
    * Builds a {@code nav_msgs/Odometry} message carrying the floating-base's exact pose (same values as the
    * corresponding {@code /tf} entry) and its exact twist - unlike {@code /tf}, this standard message type has a
    * velocity field, so consumers don't need to approximate one (e.g. by finite-differencing consecutive poses).
    * The {@code covariance} fields are unused by any known consumer and are left zero-filled.
    */
   private static byte[] buildOdometryMessage(long timestampNs, String parentFrame, String childFrame, double tx, double ty, double tz, double rx,
                                              double ry, double rz, double rw, double angularX, double angularY, double angularZ, double linearX,
                                              double linearY, double linearZ)
   {
      nav_msgs.Odometry message = new nav_msgs.Odometry();
      message.getHeader().getStamp().setSec((int) (timestampNs / 1_000_000_000L));
      message.getHeader().getStamp().setNanosec((int) (timestampNs % 1_000_000_000L));
      message.getHeader().setFrameId(parentFrame);
      message.setChildFrameId(childFrame);

      geometry_msgs.Point position = message.getPose().getPose().getPosition();
      position.setX(tx);
      position.setY(ty);
      position.setZ(tz);

      geometry_msgs.Quaternion orientation = message.getPose().getPose().getOrientation();
      orientation.setX(rx);
      orientation.setY(ry);
      orientation.setZ(rz);
      orientation.setW(rw);
      // pose.covariance[36] left zero-filled (unused by any known consumer), matching the previous message.

      geometry_msgs.Vector3 linear = message.getTwist().getTwist().getLinear();
      linear.setX(linearX);
      linear.setY(linearY);
      linear.setZ(linearZ);

      geometry_msgs.Vector3 angular = message.getTwist().getTwist().getAngular();
      angular.setX(angularX);
      angular.setY(angularY);
      angular.setZ(angularZ);
      // twist.covariance[36] left zero-filled (unused by any known consumer), matching the previous message.

      return serializeRos2Message(message);
   }

   private static byte[] buildRobotDescriptionMessage(String urdf)
   {
      std_msgs.String_ message = new std_msgs.String_();
      message.setData(urdf);
      return serializeRos2Message(message);
   }

   private static byte[] buildJointStateCdrMessage(List<byte[]> nameBytes, int[] offsets, long[] jointValues, long timestampNs)
   {
      sensor_msgs.JointState message = new sensor_msgs.JointState();
      message.getHeader().getStamp().setSec((int) (timestampNs / 1_000_000_000L));
      message.getHeader().getStamp().setNanosec((int) (timestampNs % 1_000_000_000L));
      // header.frame_id left empty, matching the previous message.

      int numJoints = nameBytes.size();
      for (byte[] nb : nameBytes)
         message.getName().add(new String(nb, StandardCharsets.UTF_8));
      for (int i = 0; i < numJoints; i++)
         message.getPosition().add(Double.longBitsToDouble(jointValues[offsets[i]]));
      for (int i = 0; i < numJoints; i++)
         message.getVelocity().add(Double.longBitsToDouble(jointValues[offsets[i] + 1]));
      // effort[] left empty, matching the previous message.

      return serializeRos2Message(message);
   }

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

   private static String findUrdfRootLink(String urdf)
   {
      Pattern linkPat  = Pattern.compile("<link\\s+name=\"([^\"]+)\"");
      Pattern childPat = Pattern.compile("<child\\s+link=\"([^\"]+)\"");
      java.util.Set<String> links    = new java.util.LinkedHashSet<>();
      java.util.Set<String> children = new java.util.LinkedHashSet<>();
      Matcher m = linkPat.matcher(urdf);
      while (m.find()) links.add(m.group(1));
      m = childPat.matcher(urdf);
      while (m.find()) children.add(m.group(1));
      links.removeAll(children);
      return links.isEmpty() ? null : links.iterator().next();
   }

   /**
    * Loads {@code urdfContent} into a plain mecano kinematic tree (no simulation engine needed) so that every
    * joint's parent-link -> child-link transform can be computed per tick from just the joint angle, instead of
    * only publishing the floating-base pose. Joints are matched to the log's {@link OneDoFState}s by name.
    */
   private static MecanoKinematics buildMecanoKinematics(String urdfContent, String urdfRootLink, List<JointState> oneDoFJoints)
   {
      try
      {
         URDFModel urdfModel = URDFTools.loadURDFModel(new ByteArrayInputStream(urdfContent.getBytes(StandardCharsets.UTF_8)),
                                                        Collections.emptyList(),
                                                        McapLogConverter.class.getClassLoader());
         // Disable kinematics simplification: it merges fixed joints (e.g. IMU/camera mounts) into their parent
         // body by default, which would drop them from the /tf tree entirely.
         URDFTools.URDFParserProperties parserProperties = new URDFTools.URDFParserProperties();
         parserProperties.setSimplifyKinematics(false);
         RobotDefinition robotDefinition = URDFTools.toRobotDefinition(urdfModel, parserProperties);
         RigidBodyBasics elevator = robotDefinition.newInstance(ReferenceFrame.getWorldFrame());
         JointBasics[] allJoints = MultiBodySystemTools.collectSubtreeJoints(elevator);

         Map<String, OneDoFJointBasics> mecanoOneDoFByName = new HashMap<>();
         List<JointBasics> jointsToPublish = new ArrayList<>();
         for (JointBasics joint : allJoints)
         {
            // The floating pelvis joint is published separately, directly from the log's raw SixDoFState data.
            if (joint.getSuccessor().getName().equals(urdfRootLink))
               continue;
            jointsToPublish.add(joint);
            if (joint instanceof OneDoFJointBasics oneDoFJoint)
               mecanoOneDoFByName.put(joint.getName(), oneDoFJoint);
         }

         OneDoFJointBasics[] mecanoOneDoFJoints = new OneDoFJointBasics[oneDoFJoints.size()];
         for (int i = 0; i < oneDoFJoints.size(); i++)
         {
            String name = oneDoFJoints.get(i).getName();
            OneDoFJointBasics match = mecanoOneDoFByName.get(name);
            if (match == null)
               System.err.println("Warning: no matching URDF joint found for logged joint '" + name + "'; its /tf transform will stay at the default pose.");
            mecanoOneDoFJoints[i] = match;
         }

         return new MecanoKinematics(elevator, jointsToPublish.toArray(new JointBasics[0]), mecanoOneDoFJoints);
      }
      catch (Exception e)
      {
         System.err.println("Warning: could not build a kinematic tree from the URDF for per-joint /tf publishing; "
                             + "only the pelvis transform will be published. Cause: " + e.getMessage());
         return null;
      }
   }

   /**
    * @param elevator             the root of the mecano tree, used to refresh all frames each tick via {@link RigidBodyBasics#updateFramesRecursively()}.
    * @param jointsToPublish      every joint except the floating pelvis joint (which is handled separately).
    * @param mecanoOneDoFJoints   parallel to the caller's {@code oneDoFJoints} list; {@code null} entries mean no URDF joint matched by name.
    */
   private record MecanoKinematics(RigidBodyBasics elevator, JointBasics[] jointsToPublish, OneDoFJointBasics[] mecanoOneDoFJoints)
   {
   }

   private static String stripNonUrdfExtensions(String urdf)
   {
      try
      {
         javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
         javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
         org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(urdf.getBytes(StandardCharsets.UTF_8)));

         // Remove <gazebo> elements — Gazebo-specific extensions not in the URDF spec.
         org.w3c.dom.NodeList gazeboNodes = doc.getElementsByTagName("gazebo");
         List<org.w3c.dom.Node> toRemove = new ArrayList<>();
         for (int i = 0; i < gazeboNodes.getLength(); i++)
            toRemove.add(gazeboNodes.item(i));
         for (org.w3c.dom.Node node : toRemove)
            node.getParentNode().removeChild(node);

         // Replace <capsule> with <cylinder> — capsule is non-standard; cylinder is the closest URDF primitive.
         org.w3c.dom.NodeList capsules = doc.getElementsByTagName("capsule");
         List<org.w3c.dom.Node> capsuleList = new ArrayList<>();
         for (int i = 0; i < capsules.getLength(); i++)
            capsuleList.add(capsules.item(i));
         for (org.w3c.dom.Node capsule : capsuleList)
         {
            org.w3c.dom.Element cylinder = doc.createElement("cylinder");
            org.w3c.dom.NamedNodeMap attrs = capsule.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++)
               cylinder.setAttribute(attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
            capsule.getParentNode().replaceChild(cylinder, capsule);
         }

         javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
         transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
         java.io.StringWriter writer = new java.io.StringWriter();
         transformer.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
         return writer.toString();
      }
      catch (Exception e)
      {
         System.err.println("Warning: could not strip Gazebo extensions from URDF, using raw content: " + e.getMessage());
         return urdf;
      }
   }

   private static String rewriteMeshPaths(String urdf)
   {
      // Relative mesh paths already start with the package directory name (e.g. "alex_V1_description/meshes/…").
      // Rewrite them to absolute HTTP URLs so the same server that serves the URDF also serves the meshes —
      // no additional Foxglove configuration required.
      return urdf.replaceAll(
            "filename=\"(?!file:|package:|http:|https:)([^\"]+)\"",
            "filename=\"http://localhost:" + HTTP_PORT + "/$1\"");
   }

   private static void extractZipIfNeeded(File zipFile, File destDir) throws IOException
   {
      try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile.toPath())))
      {
         java.util.zip.ZipEntry entry;
         while ((entry = zis.getNextEntry()) != null)
         {
            File out = new File(destDir, entry.getName());
            if (entry.isDirectory())
            {
               out.mkdirs();
            }
            else if (!out.exists())
            {
               out.getParentFile().mkdirs();
               Files.copy(zis, out.toPath());
            }
            zis.closeEntry();
         }
      }
   }

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

   /**
    * Derives a valid ROS2 "package/Resource" schema name (see {@code InterfaceTools.checkAndParsePackageResourceName}
    * in jros2-parser) from a leading-slash channel topic, e.g. "/main/AlexMPCMultiThreadControlProcess" &rarr;
    * "main/AlexMPCMultiThreadControlProcess". Passing the topic itself as the schema name leaves a leading slash,
    * which splits into an empty leading package segment and is rejected as invalid by strict ros2msg schema parsers.
    */
   private static String schemaResourceName(String topic)
   {
      return topic.startsWith("/") ? topic.substring(1) : topic;
   }

   // ── Entry point ───────────────────────────────────────────────────────────────

   // Set this to the log directory you want to convert, then run main().
   private static final String LOG_DIRECTORY = "/Users/wayne/workspaces/logger/20260529_152027_WalkingAttemptTwoStepsFailure";

   public static void main(String[] args) throws IOException
   {
      File logDirectory = new File(LOG_DIRECTORY);
      if (!logDirectory.isDirectory())
         throw new IllegalArgumentException("Not a directory: " + logDirectory.getAbsolutePath());

      // Write the MCAP and mesh resources into a dedicated sibling directory that can be
      // copied and shared as a single unit — nothing else ends up in there.
      File outputDir = new File(logDirectory.getParentFile(), logDirectory.getName() + "_foxglove");
      outputDir.mkdirs();
      File outputFile = new File(outputDir, logDirectory.getName() + ".mcap");

      File propertyFile = new File(logDirectory, YoVariableLoggerListener.propertyFile);
      LogProperties properties = new LogPropertiesReader(propertyFile);

      McapLogConverter converter = new McapLogConverter(logDirectory, properties);
      converter.convert(outputFile);
      converter.close();
   }
}
