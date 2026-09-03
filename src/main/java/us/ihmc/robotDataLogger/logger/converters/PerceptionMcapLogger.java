package us.ihmc.robotDataLogger.logger.converters;

import perception_msgs.HeightScanMessage;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.ToLongFunction;

/**
 * Subscribes to one or more perception ROS2 topics - today just the height scan, in the future e.g. one or more
 * voxel map topics too - and writes each one's messages, on its own MCAP channel, into a single shared
 * {@code perception.mcap} file for the duration of a logging session, so playback can sync any of them against the
 * rest of the log. Replaces the old one-file-per-message-type {@code HeightScanMcapLogger}: {@link McapWriter}
 * already supports any number of interleaved schemas/channels in one file (see {@link #addChannel}), so there is
 * no need for a dedicated file/{@code ROS2Node}/scrubber per source - this class just registers one more channel
 * per source.
 * <p>
 * Channels are independent of each other and of the rest of the logging session's lifecycle: a topic can start
 * publishing late, stop early, or have gaps mid-session (e.g. its upstream publisher process restarts) without
 * affecting any other channel or requiring anything special here - this class is constructed once and
 * {@link #destroy() destroyed} once per logging session (see {@code YoVariableLogger}), so each channel just
 * writes whatever arrives on its topic, whenever it arrives.
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
 * is ever renamed, this literal must be updated to match. The scs2-side reader keeps its own copy of this same
 * topic name literal (see {@code HeightMapMcapScrubber} in scs2) to pick this channel back out of
 * {@code perception.mcap} - both literals must stay in sync.
 */
public class PerceptionMcapLogger
{
   private static final String MCAP_FILENAME = "perception.mcap";

   public static final ROS2Topic<HeightScanMessage> HEIGHT_SCAN_TOPIC =
         new ROS2Topic<>("/stepping_camera/realsense/height_scan/height_scan_message", HeightScanMessage.class);

   // Concatenated ros2msg schema text (primary message, then one MSG: block per referenced type).
   // Must be kept in sync with perception_msgs/msg/HeightScanMessage.msg and its dependencies.
   private static final String HEIGHT_SCAN_SCHEMA = """
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

   /** Assigned sequentially as channels are registered in the constructor; never reused. */
   private int nextChannelId = 1;

   public PerceptionMcapLogger(File tempDirectory, File finalDirectory) throws IOException
   {
      File mcapFile = new File(tempDirectory, MCAP_FILENAME);
      mcapWriter = new McapWriter(new FileOutputStream(mcapFile, false));

      LogTools.info("Creating a ROS2Node for logging perception data to " + mcapFile);
      ros2Node = new ROS2Node(finalDirectory.getName() + "_perception_logger_node");

      addChannel(HEIGHT_SCAN_TOPIC, "perception_msgs/HeightScanMessage", HEIGHT_SCAN_SCHEMA, HeightScanMessage::getControllerTimestamp);
      // A future voxel map source is one more addChannel(...) call here, e.g.:
      // addChannel(VOXEL_MAP_TOPIC, "perception_msgs/VoxelMapMessage", VOXEL_MAP_SCHEMA, VoxelMapMessage::getControllerTimestamp);
      // It gets its own channel id and CDR buffer, multiplexed into this same perception.mcap alongside this one.
   }

   /**
    * Subscribes to {@code topic} and writes every message it receives to its own channel within the shared
    * {@code perception.mcap}, keyed by {@code timestampExtractor} (expected to be that message type's
    * controllerTimestamp field, so playback can sync this channel against the rest of the log the same way every
    * other channel does). A negative timestamp (e.g. {@code HeightScanTerm}'s {@code -1} sentinel for "no
    * RobotConfigurationData received yet") is treated as "not yet valid" and skipped, rather than logged.
    */
   private <T extends ROS2Message<T>> void addChannel(ROS2Topic<T> topic, String schemaName, String schemaText, ToLongFunction<T> timestampExtractor)
         throws IOException
   {
      int channelId = nextChannelId++;
      mcapWriter.addSchema(channelId, schemaName, "ros2msg", schemaText.getBytes(StandardCharsets.UTF_8));
      mcapWriter.addChannel(channelId, channelId, topic.getName(), "cdr", Collections.emptyMap());

      // Each channel gets its own CDR buffer: subscription callbacks for one topic are delivered sequentially, but
      // different topics' callbacks may run concurrently on separate DDS listener threads, so buffers can't be shared.
      CDRBuffer channelCdrBuffer = new CDRBuffer();
      ros2Node.createSubscriptionSampler(topic, message -> onMessage(channelId, channelCdrBuffer, topic, message, timestampExtractor));
   }

   private <T extends ROS2Message<T>> void onMessage(int channelId, CDRBuffer channelCdrBuffer, ROS2Topic<T> topic, T message,
                                                       ToLongFunction<T> timestampExtractor)
   {
      long timestamp = timestampExtractor.applyAsLong(message);
      if (timestamp < 0L)
         return;

      try
      {
         byte[] cdrBytes = serializeToCdr(channelCdrBuffer, message);
         // mcapWriter is shared across all channels, whose subscription callbacks may run on different threads -
         // writeMessage() mutates shared, non-thread-safe state (chunk buffer, per-channel indices), so it must be
         // serialized even though each channel's own CDR encoding above does not need to be.
         synchronized (mcapWriter)
         {
            mcapWriter.writeMessage(channelId, timestamp, timestamp, cdrBytes);
         }
      }
      catch (IOException e)
      {
         LogTools.error("Failed to write " + topic.getName() + " message to " + MCAP_FILENAME + ": " + e.getMessage());
      }
   }

   private static <T extends ROS2Message<T>> byte[] serializeToCdr(CDRBuffer cdrBuffer, T message)
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
         LogTools.error("Failed to close perception mcap file: " + e.getMessage());
      }
   }
}
