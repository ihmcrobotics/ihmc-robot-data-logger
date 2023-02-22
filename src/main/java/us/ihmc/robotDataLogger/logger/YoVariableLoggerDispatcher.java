package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.HashSet;

import com.martiansoftware.jsap.JSAPException;

import org.apache.commons.io.FileUtils;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.StaticHostListLoader;
import us.ihmc.robotDataLogger.interfaces.DataServerDiscoveryListener;
import us.ihmc.robotDataLogger.websocket.client.discovery.DataServerDiscoveryClient;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;

public class YoVariableLoggerDispatcher implements DataServerDiscoveryListener
{
   // Used to prevent multiple instances of the Logger running at the same time
   private final File lockFile = new File(System.getProperty("user.home") + File.separator + "loggerDispatcher.lock");

   private final DataServerDiscoveryClient discoveryClient;

   private final Object lock = new Object();

   /**
    * List of sessions for which we started a logger. This is to avoid double logging should there be
    * multiple known IPs for a single host.
    */
   private final HashSet<HashAnnouncement> activeLogSessions = new HashSet<>();

   private final YoVariableLoggerOptions options;

   /**
    * Create a new YovariableLoggerDispatcher. For every log that comes online, a YoVariableLogger is
    * created.
    *
    * @param options
    * @throws IOException
    */
   public YoVariableLoggerDispatcher(YoVariableLoggerOptions options) throws IOException
   {

      if (lockFile.exists() || modifiedTimeInFileIsCurrentTime())
      {
         LogTools.info("Maybe if you weren't so full of yourself you would have checked if the logger was already running");
         System.exit(0);
      }

      lockFile.createNewFile();
      LogTools.info("Creating Logger lock file");

      this.options = options;
      LogTools.info("Starting YoVariableLoggerDispatcher");

      boolean enableAutoDiscovery = !options.isDisableAutoDiscovery();
      discoveryClient = new DataServerDiscoveryClient(this, enableAutoDiscovery);
      discoveryClient.addHosts(StaticHostListLoader.load());

      LogTools.info("Client started, waiting for data server sessions");

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         lockFile.delete();
         lockFile.deleteOnExit();
         LogTools.info("Interrupted by Ctrl+C, deleting lock file");
      }, "ShutdownThread"));

      ThreadTools.startAThread(this::ensureLockFileExists, "CheckFileExistsThread");
      ThreadTools.sleepForever();
   }

   private boolean modifiedTimeInFileIsCurrentTime()
   {
      long currentTimeInSeconds = LocalDateTime.now().toLocalTime().toSecondOfDay();

      SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

      String fileFormattedTime = dateFormat.format(lockFile.lastModified());

      String[] hoursMinutesSeconds = fileFormattedTime.split(":");

      long fileModifiedTimeInSeconds;
      fileModifiedTimeInSeconds = Integer.parseInt(hoursMinutesSeconds[0]) * 60 * 60L;
      fileModifiedTimeInSeconds += Integer.parseInt(hoursMinutesSeconds[1]) * 60L;
      fileModifiedTimeInSeconds += Integer.parseInt(hoursMinutesSeconds[2]);

      return Math.abs(fileModifiedTimeInSeconds - currentTimeInSeconds) < 12;
   }

   private void ensureLockFileExists()
   {
      while(true)
      {
         try
         {
            ThreadTools.sleepSeconds(12);

            if (!lockFile.exists())
            {
               lockFile.createNewFile();
               LogTools.info("Lock file got deleted, creating Logger lock file");
            }

            FileUtils.touch(lockFile);

         } catch (IOException e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   public static void main(String[] args) throws JSAPException, IOException
   {
      YoVariableLoggerOptions options = YoVariableLoggerOptions.parse(args);
      new YoVariableLoggerDispatcher(options);
   }

   @Override
   public void connected(HTTPDataServerConnection connection)
   {
      synchronized (lock)
      {
         Announcement announcement = connection.getAnnouncement();
         HashAnnouncement hashAnnouncement = new HashAnnouncement(announcement);
         LogTools.warn("New control session came online\n" + connection.getTarget() + " (" + announcement.getHostNameAsString() + ")");
         if (activeLogSessions.contains(hashAnnouncement))
         {
            LogTools.warn("A logging sessions for " + announcement.getNameAsString() + " is already started.");
         }
         else
         {
            if (announcement.getLog())
            {
               try
               {
                  new YoVariableLogger(connection, options, (request) -> finishedLog(request));
                  activeLogSessions.add(hashAnnouncement);
                  LogTools.info("Logging session started for " + announcement.getNameAsString());
               }
               catch (Exception e)
               {
                  e.printStackTrace();
               }
            }
            else
            {
               LogTools.info("Not logging.");
            }
         }
      }
   }

   @Override
   public void disconnected(HTTPDataServerConnection connection)
   {
   }

   /**
    * When a log is finished succesfully, this function removes the active session from the list of
    * sessions This is useful in the case the network connection to the robot gets interrupted. When
    * the robot regains network connectivity, a new log will start.
    *
    * @param request
    */
   private void finishedLog(Announcement request)
   {
      LogTools.info("Finishing Log.");
      synchronized (lock)
      {
         //pause a bit to ensure everything is closed before removing the active log session
         ThreadTools.sleep(2000);
         LogTools.info("Removing log session.");
         HashAnnouncement hashRequest = new HashAnnouncement(request);
         activeLogSessions.remove(hashRequest);
         LogTools.info("Logging session for " + request.getNameAsString() + " has finished.");

      }
   }

   /**
    * Simple hashcode calculator for announcements to allow it in a HashSet
    *
    * @author Jesper Smith
    */
   private static class HashAnnouncement
   {
      private final Announcement announcement;

      public HashAnnouncement(Announcement announcement)
      {
         this.announcement = announcement;
      }

      @Override
      public boolean equals(Object other)
      {
         if (other instanceof HashAnnouncement)
         {
            return announcement.equals(((HashAnnouncement) other).announcement);
         }
         else
         {
            return false;
         }
      }

      @Override
      public int hashCode()
      {
         final int prime = 31;
         int result = 1;
         result = prime * result + announcement.getIdentifierAsString().hashCode();
         result = prime * result + (announcement.getLog() ? 1231 : 1237);
         result = prime * result + announcement.getNameAsString().hashCode();
         result = prime * result + announcement.getReconnectKeyAsString().hashCode();
         return result;
      }
   }
}
