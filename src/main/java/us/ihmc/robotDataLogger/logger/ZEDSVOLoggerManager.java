package us.ihmc.robotDataLogger.logger;

import us.ihmc.robotDataLogger.ZEDSDKAnnounce;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2NodeBuilder;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.ROS2TopicNameTools;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages n number of ZED SDK connections for logging SVO files.
 * Listens on {@link #ZED_SDK_ANNOUNCE_TOPIC} for connection information.
 */
public class ZEDSVOLoggerManager
{
   public static final ROS2Topic<ZEDSDKAnnounce> ZED_SDK_ANNOUNCE_TOPIC = new ROS2Topic<ZEDSDKAnnounce>().withType(ZEDSDKAnnounce.class)
                                                                                                         .withSuffix("zed_sdk_announce");

   private record ZEDSDKAnnounceHash(String address, int port)
   {
   }

   private final File tempDirectory;

   private final ROS2Node ros2Node;
   private final Map<ZEDSDKAnnounceHash, ZEDSVOLogger> zedLoggers = new ConcurrentHashMap<>();

   public ZEDSVOLoggerManager(File tempDirectory, File finalDirectory)
   {
      this.tempDirectory = tempDirectory;

      ros2Node = new ROS2NodeBuilder().build(ROS2TopicNameTools.toROSTopicFormat(finalDirectory.getName() + "_zed_svo_logger_node"));

      ros2Node.createSubscription2(ZED_SDK_ANNOUNCE_TOPIC, this::onZEDSDKAnnounceMessage);
   }

   private void onZEDSDKAnnounceMessage(ZEDSDKAnnounce message)
   {
      ZEDSDKAnnounceHash announceHash = new ZEDSDKAnnounceHash(message.getAddressAsString(), message.getPort());

      if (zedLoggers.containsKey(announceHash))
      {
         ZEDSVOLogger zedSVOLogger = zedLoggers.get(announceHash);

         if (zedSVOLogger.completelyStopped() && !zedSVOLogger.failedBeyondRecovery())
         {
            zedLoggers.remove(announceHash);
         }
      }
      else
      {
         File perceptionDir = new File(tempDirectory, "perception");
         perceptionDir.mkdirs();
         String svoFile = perceptionDir.getAbsolutePath() + File.separator + generateSVOFileName();

         ZEDSVOLogger zedSVOLogger = new ZEDSVOLogger();

         zedSVOLogger.start(svoFile, message.getAddressAsString(), message.getPort());

         zedLoggers.put(announceHash, zedSVOLogger);
      }
   }

   public void destroy()
   {
      zedLoggers.forEach((hostInstanceID, zedSVOLogger) -> zedSVOLogger.stop());

      ros2Node.destroy();
   }

   private static String generateSVOFileName()
   {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      return dateFormat.format(new Date()) + "_" + "ZEDRecording.svo2";
   }
}
