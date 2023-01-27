package us.ihmc.robotDataLogger.logReport;

import us.ihmc.robotDataLogger.Announcement;

public class LogFinishedSlackNotifyListener extends LogFinishedReporterListener
{
   @Override
   public void logSessionReport(Announcement announcement, LogReport logReport)
   {
      // TODO: send log report to slack
   }
}
