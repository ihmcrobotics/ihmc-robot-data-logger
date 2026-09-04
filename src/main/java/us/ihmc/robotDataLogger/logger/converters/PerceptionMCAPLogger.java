package us.ihmc.robotDataLogger.logger.converters;

import perception_msgs.HeightScanMessage;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.handshake.LoggingROS2API;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.ToLongFunction;

/**
 * Subscribes to one or more perception ROS2 topics
 * Channels are independent of each other and of the rest of the logging session's lifecycle: a topic can start
 * publishing late, stop early, or have gaps mid-session (e.g. its upstream publisher process restarts) without
 * affecting any other channel or requiring anything special here - this class is constructed once and
 * {@link #destroy() destroyed} once per logging session (see {@code YoVariableLogger}), so each channel just
 * writes whatever arrives on its topic, whenever it arrives.
 * <p>
 */
public class PerceptionMCAPLogger
{
   private static final String MCAP_FILENAME = "perception.mcap";

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

   public PerceptionMCAPLogger(File tempDirectory, File finalDirectory) throws IOException
   {
      File mcapFile = new File(tempDirectory, MCAP_FILENAME);
      mcapWriter = new McapWriter(new FileOutputStream(mcapFile, false));

      LogTools.info("Creating a ROS2Node for logging perception data to " + mcapFile);
      ros2Node = new ROS2Node(finalDirectory.getName() + "_perception_logger_node");

      addChannel(LoggingROS2API.STEPPING_HEIGHT_SCAN, "perception_msgs/HeightScanMessage", HEIGHT_SCAN_SCHEMA, HeightScanMessage::getControllerTimestamp);
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
