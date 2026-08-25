package us.ihmc.robotDataLogger.logger;

import logger_msgs.Announcement;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.YoVariableClient;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class YoVariableLogger
{
   // changed to a 10s timeout for camp lejeune demo
   public static final int timeout = 25000; // 2500;

   // Backstop for this session logging for long enough to fill the disk well after the check at
   // session start (below) already passed. Only runs while this controller session is logging.
   private static final long CRITICAL_DISK_SPACE_CHECK_PERIOD_MINUTES = 1;

   private final YoVariableLoggerOptions options;

   private final ScheduledExecutorService criticalDiskSpaceCheckExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
                                                                                                                      {
                                                                                                                         Thread thread = new Thread(runnable,
                                                                                                                                                    getClass().getName()
                                                                                                                                                    + "-CriticalDiskSpaceCheck");
                                                                                                                         thread.setDaemon(true);
                                                                                                                         return thread;
                                                                                                                      });

   private ZEDSVOLoggerManager zedSVOLoggerManager;

   public YoVariableLogger(HTTPDataServerConnection connection, YoVariableLoggerOptions options, Consumer<Announcement> doneListener) throws IOException
   {
      this.options = options;
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

      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(logDirectory);

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

      criticalDiskSpaceCheckExecutor.scheduleAtFixedRate(() -> checkCriticalDiskSpace(logDirectory),
                                                         CRITICAL_DISK_SPACE_CHECK_PERIOD_MINUTES,
                                                         CRITICAL_DISK_SPACE_CHECK_PERIOD_MINUTES,
                                                         TimeUnit.MINUTES);

      if (!options.getDisableZEDLogging())
         zedSVOLoggerManager = new ZEDSVOLoggerManager(tempDirectory, finalDirectory);
   }

   private static void checkCriticalDiskSpace(Path logDirectory)
   {
      try
      {
         YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileCriticallyLowOnSpace(logDirectory);
      }
      catch (IOException e)
      {
         LogTools.error("Failed to check/clean up critically low disk space in " + logDirectory + ": " + e.getMessage());
      }
   }

   public void destroy()
   {
      criticalDiskSpaceCheckExecutor.shutdownNow();

      if (!options.getDisableZEDLogging())
         zedSVOLoggerManager.destroy();
   }
}
