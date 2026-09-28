package us.ihmc.robotDataLogger.logger;

import logger_msgs.ZEDSDKAnnounce;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.log.LogTools;
import us.ihmc.zed.library.ZEDJavaAPINativeLibrary;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages n number of ZED SDK connections for logging SVO files.
 * Listens on {@link #ZED_SDK_ANNOUNCE_TOPIC} for connection information.
 */
public class ZEDSVOLoggerManager
{
   private static final boolean ZED_SDK_LOADED = ZEDJavaAPINativeLibrary.load();
   private static final long DESTROY_TIMEOUT_MILLIS = 10_000;

   public static final ROS2Topic<ZEDSDKAnnounce> ZED_SDK_ANNOUNCE_TOPIC = new ROS2Topic<ZEDSDKAnnounce>().withType(ZEDSDKAnnounce.class)
                                                                                                         .prependedWith("zed_sdk_announce");

   private record ZEDSDKAnnounceHash(String address, int port)
   {
   }

   private final File tempDirectory;

   private final ROS2Node ros2Node;
   private final Map<ZEDSDKAnnounceHash, ZEDSVOLogger> zedLoggers = new ConcurrentHashMap<>();
   private final Object lock = new Object();
   private boolean destroyed = false;

   public ZEDSVOLoggerManager(File tempDirectory, File finalDirectory)
   {
      this.tempDirectory = tempDirectory;

      LogTools.info("Creating a ROS2Node for listening to ZED SDK connections.");
      ros2Node = new ROS2Node(finalDirectory.getName() + "_zed_svo_logger_node");

      if (ZED_SDK_LOADED)
         ros2Node.createSubscriptionSampler(ZED_SDK_ANNOUNCE_TOPIC, this::onZEDSDKAnnounceMessage);
      else
         LogTools.info("ZED SDK not available on the system. Will not attempt to log SVO files.");
   }

   private void onZEDSDKAnnounceMessage(ZEDSDKAnnounce message)
   {
      // Hold the lock for the whole message so a new logger can't be created while destroy() is closing them
      synchronized (lock)
      {
         if (!destroyed)
            handleZEDSDKAnnounceMessage(message);
      }
   }

   private void handleZEDSDKAnnounceMessage(ZEDSDKAnnounce message)
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
      catch (ArrayIndexOutOfBoundsException ignored)
      {
      }

      ZEDSDKAnnounceHash announceHash = new ZEDSDKAnnounceHash(message.getAddressAsString(), message.getPort());

      ZEDSVOLogger existingLogger = zedLoggers.get(announceHash);
      if (existingLogger != null && existingLogger.isClosed())
      {
         // The ZED stopped grabbing or never connected, drop it so it can reconnect below
         zedLoggers.remove(announceHash);
         existingLogger = null;
      }

      if (existingLogger != null)
      {
         if (message.getControllerTimestamp() != 0)
            existingLogger.synchronize(message);
      }
      else if (message.getControllerTimestamp() != 0)
      {
         File perceptionDir = new File(tempDirectory, "perception");
         perceptionDir.mkdirs();
         String svoFile = perceptionDir.getAbsolutePath() + File.separator + generateSVOFileName(message);
         String datFile = perceptionDir.getAbsolutePath() + File.separator +
                 "%s%s".formatted(message.getSensorNameAsString(), VideoDataLoggerInterface.timestampDataPostfix);

         ZEDSVOLogger zedSVOLogger;
         try
         {
            zedSVOLogger = new ZEDSVOLogger();
         }
         catch (IllegalStateException e)
         {
            LogTools.error("Can't log " + message.getSensorNameAsString() + ": " + e.getMessage());
            return;
         }

         zedSVOLogger.connect(svoFile,
                              datFile,
                              message.getAddressAsString(),
                              message.getPort(),
                              message.getFps(),
                              message.getBitrate(),
                              message.getSensorTimestamp(),
                              message.getControllerTimestamp());

         zedLoggers.put(announceHash, zedSVOLogger);
      }
   }

   /**
    * Closes all the ZED loggers in parallel. Returns after at most {@link #DESTROY_TIMEOUT_MILLIS} even if a ZED
    * is stuck in the ZED SDK, so the log can still be finished and the next log session can start.
    */
   public void destroy()
   {
      List<ZEDSVOLogger> loggersToClose;
      synchronized (lock)
      {
         destroyed = true;
         loggersToClose = new ArrayList<>(zedLoggers.values());
         zedLoggers.clear();
      }

      // Can't use LogTools in here, we might be shutting down...
      System.out.println("Closing " + loggersToClose.size() + " ZED SVO logger(s)");

      ExecutorService closeExecutor = Executors.newCachedThreadPool(ThreadTools.createNamedDaemonThreadFactory(getClass().getSimpleName() + "Close"));
      List<Future<?>> closeFutures = new ArrayList<>();
      for (ZEDSVOLogger zedSVOLogger : loggersToClose)
      {
         closeFutures.add(closeExecutor.submit(zedSVOLogger::close));
      }
      Future<?> ros2NodeCloseFuture = closeExecutor.submit(ros2Node::close);

      long deadline = System.currentTimeMillis() + DESTROY_TIMEOUT_MILLIS;
      for (int i = 0; i < loggersToClose.size(); i++)
      {
         waitForClose(closeFutures.get(i), deadline, loggersToClose.get(i).getName());
      }
      waitForClose(ros2NodeCloseFuture, deadline, "ZED SDK announce ROS2Node");

      closeExecutor.shutdownNow();
   }

   private static void waitForClose(Future<?> closeFuture, long deadline, String name)
   {
      try
      {
         closeFuture.get(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException e)
      {
         System.err.println(name + " did not close within " + DESTROY_TIMEOUT_MILLIS + " ms, abandoning it so the log can finish");
      }
      catch (InterruptedException | ExecutionException e)
      {
         e.printStackTrace();
      }
   }

   private static String generateSVOFileName(ZEDSDKAnnounce message)
   {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      return "%s_%s.svo2".formatted(dateFormat.format(new Date()), message.getSensorNameAsString());
   }
}
