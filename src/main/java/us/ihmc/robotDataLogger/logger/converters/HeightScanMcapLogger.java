package us.ihmc.robotDataLogger.logger.converters;

import perception_msgs.HeightScanMessage;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Subscribes to the controller's height scan topic and writes each message to its own MCAP file
 * for the duration of a logging session, timestamped by the message's own controllerTimestamp
 * field so it can be synced against the rest of the log during playback.
 * <p>
 * {@code perception_msgs.HeightScanMessage} (and its {@code Vector2}/{@code PackedElementField}
 * dependencies, plus the {@code geometry_msgs} types {@code pose} needs) are generated in THIS repo
 * ({@code perception_msgs/msg/}, {@code geometry_msgs/msg/}) - this is the canonical definition, not
 * a copy. {@code PerceptionAPI} (in ihmc-communication) and {@code HeightScanTerm} (in
 * ihmc-closed-source-control) both reference this same package directly, since ihmc-communication
 * already depends on ihmc-robot-data-logger - no new dependency edge needed on their side. This repo
 * deliberately does NOT depend on ihmc-interfaces-jros2 or ihmc-communication: the former would be
 * redundant now that the message lives here, and the latter would be circular (it already depends on
 * ihmc-robot-data-logger).
 * <p>
 * One consequence: {@link #HEIGHT_SCAN_TOPIC} can't reference {@code PerceptionAPI.STEPPING_HEIGHT_SCAN}
 * directly (still in ihmc-communication, still undependable-on from here), so its name is a literal
 * copy read from {@code PerceptionAPI.STEPPING_HEIGHT_SCAN.getName()} at the time this was written -
 * it is NOT rebuilt using {@code HumanoidROS2Topic}-style prefix/module/suffix/type-name assembly,
 * since the plain {@link ROS2Topic} used here composes names differently. If {@code STEPPING_HEIGHT_SCAN}
 * is ever renamed, this literal must be updated to match.
 */
public class HeightScanMcapLogger
{
   private static final String MCAP_FILENAME = "heightScan.mcap";
   private static final int CHANNEL_ID = 1;

   public static final ROS2Topic<HeightScanMessage> HEIGHT_SCAN_TOPIC =
         new ROS2Topic<>("/stepping_camera/realsense/height_scan/height_scan_message", HeightScanMessage.class);

   // Concatenated ros2msg schema text (primary message, then one MSG: block per referenced type).
   // Must be kept in sync with perception_msgs/msg/HeightScanMessage.msg and its dependencies.
   private static final String SCHEMA = """
         uint64 sequence_id
         int64 controllerTimestamp
         string frame_id
         geometry_msgs/Pose pose
         uint32 column_count
         perception_msgs/Vector2 cell_size
         uint32 row_stride
         uint32 cell_stride
         perception_msgs/PackedElementField[] fields
         uint8[] data

         ================================================================================
         MSG: geometry_msgs/Pose
         Point position
         Quaternion orientation

         ================================================================================
         MSG: geometry_msgs/Point
         float64 x
         float64 y
         float64 z

         ================================================================================
         MSG: geometry_msgs/Quaternion
         float64 x
         float64 y
         float64 z
         float64 w

         ================================================================================
         MSG: perception_msgs/Vector2
         float64 x
         float64 y

         ================================================================================
         MSG: perception_msgs/PackedElementField
         string name
         uint32 offset
         uint8 UNKNOWN=0
         uint8 UINT8=1
         uint8 INT8=2
         uint8 UINT16=3
         uint8 INT16=4
         uint8 UINT32=5
         uint8 INT32=6
         uint8 FLOAT32=7
         uint8 FLOAT64=8
         uint8 type
         """;

   private final ROS2Node ros2Node;
   private final McapWriter mcapWriter;
   private final CDRBuffer cdrBuffer = new CDRBuffer();

   public HeightScanMcapLogger(File tempDirectory, File finalDirectory) throws IOException
   {
      File mcapFile = new File(tempDirectory, MCAP_FILENAME);
      mcapWriter = new McapWriter(new FileOutputStream(mcapFile, false));
      mcapWriter.addSchema(CHANNEL_ID, "perception_msgs/HeightScanMessage", "ros2msg", SCHEMA.getBytes(StandardCharsets.UTF_8));
      mcapWriter.addChannel(CHANNEL_ID, CHANNEL_ID, HEIGHT_SCAN_TOPIC.getName(), "cdr", Collections.emptyMap());

      LogTools.info("Creating a ROS2Node for logging height scan data to " + mcapFile);
      ros2Node = new ROS2Node(finalDirectory.getName() + "_height_scan_logger_node");
      ros2Node.createSubscriptionSampler(HEIGHT_SCAN_TOPIC, this::onHeightScanMessage);
   }

   private void onHeightScanMessage(HeightScanMessage message)
   {
      // -1 is HeightScanTerm's sentinel for "no RobotConfigurationData received yet" - not useful to log.
      if (message.getControllerTimestamp() == -1L)
         return;

      try
      {
         mcapWriter.writeMessage(CHANNEL_ID, message.getControllerTimestamp(), message.getControllerTimestamp(), serializeToCdr(message));
      }
      catch (IOException e)
      {
         LogTools.error("Failed to write height scan message to mcap file: " + e.getMessage());
      }
   }

   private byte[] serializeToCdr(HeightScanMessage message)
   {
      cdrBuffer.rewind();
      int payloadSize = message.calculateSizeBytes(CDRBuffer.PAYLOAD_HEADER.length);
      cdrBuffer.ensureRemainingCapacity(CDRBuffer.PAYLOAD_HEADER.length + payloadSize);
      cdrBuffer.writePayloadHeader();
      message.serialize(cdrBuffer);

      ByteBuffer written = cdrBuffer.getBufferUnsafe().duplicate();
      written.flip();
      byte[] bytes = new byte[written.remaining()];
      written.get(bytes);
      return bytes;
   }

   public void destroy()
   {
      ros2Node.close();
      try
      {
         mcapWriter.close();
      }
      catch (IOException e)
      {
         LogTools.error("Failed to close height scan mcap file: " + e.getMessage());
      }
   }
}
