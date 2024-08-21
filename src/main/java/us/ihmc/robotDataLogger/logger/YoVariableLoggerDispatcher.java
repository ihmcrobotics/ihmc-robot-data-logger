package us.ihmc.robotDataLogger.logger;

import com.martiansoftware.jsap.JSAPException;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.StaticHostListLoader;
import us.ihmc.robotDataLogger.interfaces.DataServerDiscoveryListener;
import us.ihmc.robotDataLogger.websocket.DataServerLocationBroadcast;
import us.ihmc.robotDataLogger.websocket.client.discovery.DataServerDiscoveryClient;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;

import java.io.IOException;
import java.util.HashSet;

public class YoVariableLoggerDispatcher implements DataServerDiscoveryListener
{
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
      this.options = options;
      LogTools.info("Starting YoVariableLoggerDispatcher");

      boolean enableAutoDiscovery = !options.isDisableAutoDiscovery();
      discoveryClient = new DataServerDiscoveryClient(this, enableAutoDiscovery);
      discoveryClient.addHosts(StaticHostListLoader.load());

      // We allow for multiple instances of the logger to be run on the same machine
      if (!options.isAllowManyInstances() && discoveryClient.getBindException() != null)
      {
         LogTools.error("The bind multicast port (" + DataServerLocationBroadcast.announcePort + ") is in use. Is there another logger running?");
         ThreadTools.sleep((long) Conversions.secondsToMilliseconds(3));
         LogTools.error("Shutting down...");
         System.exit(0);
      }

      LogTools.info("Client started, waiting for data server sessions");

      ThreadTools.sleepForever();
   }

   public static void main(String[] args) throws JSAPException, IOException, InterruptedException
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
         LogTools.warn("New control session came online: ( {} ({}))", connection.getTarget(), announcement.getHostNameAsString());
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
               LogTools.warn("Not logging the above session");
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
      synchronized (lock)
      {
         // Pause a bit to ensure everything is closed before removing the active log session
         // Then remove this session from the list of active log sessions
         ThreadTools.sleep(2000);
         HashAnnouncement hashRequest = new HashAnnouncement(request);
         activeLogSessions.remove(hashRequest);
         LogTools.info("Logging session for " + request.getNameAsString() + " has finished.\n");
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
