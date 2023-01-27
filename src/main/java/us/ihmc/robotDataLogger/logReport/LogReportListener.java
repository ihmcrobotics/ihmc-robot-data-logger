package us.ihmc.robotDataLogger.logReport;

import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.listeners.LogAnnouncementListener;

public abstract class LogReportListener implements LogAnnouncementListener
{
   @Override
   public void logSessionCameOnline(Announcement announcement)
   {
      // Do nothing
   }

   @Override
   public void logSessionWentOffline(Announcement announcement)
   {
      // TODO: generate log report and save to file server

      LogReport logReport = new LogReport();
      logSessionReport(announcement, logReport);
   }

   public abstract void logSessionReport(Announcement announcement, LogReport logReport);

}
