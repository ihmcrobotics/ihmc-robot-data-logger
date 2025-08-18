package us.ihmc.robotDataLogger.logger;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.ZEDSDKAnnounce;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2NodeBuilder;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.ROS2TopicNameTools;
import us.ihmc.zed.library.ZEDJavaAPINativeLibrary;

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
   private static final boolean ZED_SDK_LOADED = ZEDJavaAPINativeLibrary.load();

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

      LogTools.info("Creating a ROS2Node for listening to ZED SDK connections.");
      ros2Node = new ROS2NodeBuilder().build(ROS2TopicNameTools.toROSTopicFormat(finalDirectory.getName() + "_zed_svo_logger_node"));

      if (ZED_SDK_LOADED)
         ros2Node.createSubscription2(ZED_SDK_ANNOUNCE_TOPIC, this::onZEDSDKAnnounceMessage);
      else
         LogTools.info("ZED SDK not available on the system. Will not attempt to log SVO files.");
   }

   private void onZEDSDKAnnounceMessage(ZEDSDKAnnounce message)
   {
      // TODO: Make a proper fix here
      /*
       * This is a temp hacky fix to prevent log sessions from logging SVO's from different robots.
       *
       * E.g.
       * RobotA with ZED sensor
       * RobotB with no ZED sensor
       *
       * Logger session for RobotB should not be trying to connect to the remote ZED SDK connection for RobotA.
       * We assume the sensor name starts with the robot name (RobotAZED).
       */
      try
      {
         String secondWordInTempDirName = tempDirectory.getName().substring(1).split("(?=[A-Z])")[1];
         if (!message.getSensorNameAsString().startsWith(secondWordInTempDirName))
         {
            return;
         }
      }
      catch (ArrayIndexOutOfBoundsException e)
      {
         return;
      }

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
         String svoFile = perceptionDir.getAbsolutePath() + File.separator + generateSVOFileName(message);
         String datFile = perceptionDir.getAbsolutePath() + File.separator + 
                 "%s%s".formatted(message.getSensorNameAsString(), VideoDataLoggerInterface.timestampDataPostfix);

         ZEDSVOLogger zedSVOLogger = new ZEDSVOLogger();

         zedSVOLogger.start(svoFile, datFile,
                            message.getAddressAsString(),
                            message.getPort(),
                            message.getFps(),
                            message.getBitrate(),
                            message.getSensorTimestamp(),
                            message.getControllerTimestamp());

         zedLoggers.put(announceHash, zedSVOLogger);
      }
   }

   public void destroy()
   {
      zedLoggers.forEach((hostInstanceID, zedSVOLogger) -> zedSVOLogger.stop());

      ros2Node.destroy();
   }

   private static String generateSVOFileName(ZEDSDKAnnounce message)
   {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      return "%s_%s.svo2".formatted(dateFormat.format(new Date()), message.getSensorNameAsString());
   }
}
