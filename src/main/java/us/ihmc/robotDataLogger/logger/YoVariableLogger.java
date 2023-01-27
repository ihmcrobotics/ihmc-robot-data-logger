package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.function.Consumer;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.YoVariableClient;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;

public class YoVariableLogger
{
   // changed to a 10s timeout for camp lejeune demo
   public static final int timeout = 25000; // 2500;

   private final YoVariableClient client;

   public YoVariableLogger(HTTPDataServerConnection connection, YoVariableLoggerOptions options, Consumer<Announcement> doneListener) throws IOException
   {
      Path logDirectory = Paths.get(options.getLogDirectory());
      
      if(!Files.exists(logDirectory))
      {
         // Log directory does not exist. Try making it
         LogTools.info("Creating directory for logs in " + logDirectory);
         Files.createDirectories(logDirectory);
      }
      else if (!Files.isDirectory(logDirectory))
      {
         throw new IOException("Desired path for storing logs is not a directory: " + logDirectory);
      }
      
      if(options.isRotateLogs())
      {
         YoVariableLogRotator.rotate(logDirectory, options.getNumberOfLogsToKeep());
      }
      
      Announcement request = connection.getAnnouncement();

      DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      Calendar calendar = Calendar.getInstance();
      String timestamp = dateFormat.format(calendar.getTime());
      String timestampName = timestamp + "_" + request.getName();
      request.setTimestampName(timestampName);

      File tempDirectory = new File(logDirectory.toFile(), "." + timestampName);

      File finalDirectory = new File(logDirectory.toFile(), timestampName);
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
         throw new IOException("Cannot create directory " + finalDirectory.getAbsolutePath());
      }

      YoVariableLoggerListener logger = new YoVariableLoggerListener(tempDirectory, finalDirectory, timestamp, request, connection.getTarget(), options, doneListener);
      client = new YoVariableClient(logger);

      try
      {
         client.start(timeout, connection);
      }
      catch (IOException e)
      {
         finalDirectory.delete();
         throw e;
      }
   }
}
