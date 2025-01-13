package us.ihmc.robotDataLogger.logger;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.YoVariableClient;
import us.ihmc.robotDataLogger.ZEDSDKAnnounce;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2NodeBuilder;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.ROS2TopicNameTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class YoVariableLogger
{
   // changed to a 10s timeout for camp lejeune demo
   public static final int timeout = 25000; // 2500;
   public static final ROS2Topic<ZEDSDKAnnounce> ZED_SDK_ANNOUNCE_TOPIC = new ROS2Topic<ZEDSDKAnnounce>().withType(ZEDSDKAnnounce.class)
                                                                                                         .withSuffix("zed_sdk_announce");

   private record ZEDSDKAnnounceHash(String address, int port) {}

   private final ROS2Node ros2Node;
   private final Map<ZEDSDKAnnounceHash, ZEDSVOLogger> zedLoggers = new ConcurrentHashMap<>();

   public YoVariableLogger(HTTPDataServerConnection connection, YoVariableLoggerOptions options, Consumer<Announcement> doneListener) throws IOException
   {
      Path logDirectory = Paths.get(options.getLogDirectory());

      if (!Files.exists(logDirectory))
      {
         // Log directory does not exist. Try making it
         LogTools.info("Creating directory for logs in " + logDirectory);
         Files.createDirectories(logDirectory);
      }
      else if (!Files.isDirectory(logDirectory))
      {
         throw new IOException("Desired path for storing logs is not a directory: " + logDirectory);
      }

      if (options.isRotateLogs())
      {
         YoVariableLogRotator.rotate(logDirectory, options.getNumberOfLogsToKeep());
      }

      Announcement request = connection.getAnnouncement();

      DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      Calendar calendar = Calendar.getInstance();
      String timestamp = dateFormat.format(calendar.getTime());

      File tempDirectory = new File(logDirectory.toFile(), "." + timestamp + "_" + request.getName());

      File finalDirectory = new File(logDirectory.toFile(), timestamp + "_" + request.getName());
      if (finalDirectory.exists())
      {
         throw new IOException("Directory " + finalDirectory.getAbsolutePath() + " already exists");
      }

      if (tempDirectory.exists())
      {
         throw new IOException("Temp directory " + finalDirectory.getAbsolutePath() + " already exists");
      }
      if (!tempDirectory.mkdir())
      {
         throw new IOException("Cannot create directory " + finalDirectory.getAbsolutePath()
                               + "\nThis is likely due to the fact your Logger storage is full... maybe get some better funding and buy some more storage hot shot");
      }

      YoVariableLoggerListener logger = new YoVariableLoggerListener(tempDirectory,
                                                                     finalDirectory,
                                                                     timestamp,
                                                                     request,
                                                                     connection.getTarget(),
                                                                     options,
                                                                     doneListener);
      YoVariableClient client = new YoVariableClient(logger);

      try
      {
         client.start(timeout, connection);
      }
      catch (IOException e)
      {
         finalDirectory.delete();
         throw e;
      }

      ros2Node = new ROS2NodeBuilder().build(ROS2TopicNameTools.toROSTopicFormat(finalDirectory.getName() + "_node"));

      ros2Node.createSubscription2(ZED_SDK_ANNOUNCE_TOPIC, message ->
      {
         File perceptionDir = new File(tempDirectory, "perception");
         perceptionDir.mkdirs();
         String svoFile = perceptionDir.getAbsolutePath() + "/" + generateSVOFileName();

         ZEDSDKAnnounceHash announceHash = new ZEDSDKAnnounceHash(message.getAddressAsString(), message.getPort());

         if (zedLoggers.containsKey(announceHash))
         {
            ZEDSVOLogger zedSVOLogger = zedLoggers.get(announceHash);

            if (zedSVOLogger.stopped() && !zedSVOLogger.failedBeyondRecovery())
            {
               zedLoggers.remove(announceHash);
            }
         }
         else
         {
            ZEDSVOLogger zedSVOLogger = new ZEDSVOLogger();

            zedSVOLogger.start(svoFile, message.getAddressAsString(), message.getPort());

            zedLoggers.put(announceHash, zedSVOLogger);
         }
      });
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
