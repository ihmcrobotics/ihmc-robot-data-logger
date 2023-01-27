package us.ihmc.robotDataLogger.logReport;

import us.ihmc.robotDataLogger.Announcement;

public class LogFinishedSlackNotifyListener extends LogFinishedReporterListener
{

   private String slackWebhookURL;
   private String slackChannelName; // e.g. "#robot-log-report"

   public LogFinishedSlackNotifyListener(String slackWebhookURL, String slackChannelName)
   {
      this.slackWebhookURL = slackWebhookURL;
      this.slackChannelName = slackChannelName;
   }

   @Override
   public void logSessionReport(Announcement announcement, LogReport logReport)
   {
      System.out.println("notify slack");
      // TODO: send log report to slack
   }

   private class SlackLogMessageBuilder
   {
      private String logDirectory;

      public SlackLogMessageBuilder logDirectory(String logDirectory)
      {
         this.logDirectory = logDirectory;
         return this;
      }

      public String build()
      {
         StringBuilder stringBuilder = new StringBuilder();

         stringBuilder.append("A new robot data log has been generated.");
         stringBuilder.append(" ");
         stringBuilder.append("");

         return stringBuilder.toString();
      }
   }
}
