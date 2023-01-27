package us.ihmc.robotDataLogger.logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.martiansoftware.jsap.JSAPException;

import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.StaticHostListLoader;
import us.ihmc.robotDataLogger.interfaces.DataServerDiscoveryListener;
import us.ihmc.robotDataLogger.listeners.LogAnnouncementListener;
import us.ihmc.robotDataLogger.logReport.LogFinishedSlackNotifyListener;
import us.ihmc.robotDataLogger.websocket.client.discovery.DataServerDiscoveryClient;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;

public class YoVariableLoggerDispatcher implements DataServerDiscoveryListener
{
   private final DataServerDiscoveryClient discoveryClient;

   private final Object lock = new Object();

   private final List<LogAnnouncementListener> logAnnouncementListeners = new ArrayList<>();

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
      this.options = options;
      LogTools.info("Starting YoVariableLoggerDispatcher");

      boolean enableAutoDiscovery = !options.isDisableAutoDiscovery();
      discoveryClient = new DataServerDiscoveryClient(this, enableAutoDiscovery);
      discoveryClient.addHosts(StaticHostListLoader.load());

      // Register default listeners
      LogFinishedSlackNotifyListener slackNotifyListener = new LogFinishedSlackNotifyListener(options);
      registerLogAnnouncementListener(slackNotifyListener);

      LogTools.info("Client started, waiting for data server sessions");
      
      ThreadTools.sleepForever();
   }

   public static void main(String[] args) throws JSAPException, IOException
   {
      YoVariableLoggerOptions options = YoVariableLoggerOptions.parse(args);
      new YoVariableLoggerDispatcher(options);
   }

   public void registerLogAnnouncementListener(LogAnnouncementListener listener)
   {
      logAnnouncementListeners.add(listener);
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
                  new YoVariableLogger(connection, options, this::finishedLog);
                  activeLogSessions.add(hashAnnouncement);
                  logAnnouncementListeners.forEach(listener -> listener.logSessionCameOnline(announcement));
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
         logAnnouncementListeners.forEach(listener -> listener.logSessionWentOffline(request));
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
