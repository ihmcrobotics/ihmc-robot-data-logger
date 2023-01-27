package us.ihmc.robotDataLogger.logReport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.ihmc.commons.nio.FileTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class LogReportSlackListener extends LogReportListener
{
   private static final String SLACK_SETTINGS_FILE_NAME = "slackSettings.properties";
   private SlackLogSettings slackLogSettings;

   public LogReportSlackListener(YoVariableLoggerOptions options)
   {
      File slackSettingsFile = new File(options.getLogDirectory(), SLACK_SETTINGS_FILE_NAME);
      slackLogSettings = new SlackLogSettings(slackSettingsFile);

      try
      {
         slackLogSettings.load();
      }
      catch (IOException e)
      {
         e.printStackTrace();
         LogTools.warn("Could not load " + slackSettingsFile.getAbsolutePath());
      }

      if (!slackLogSettings.loaded())
      {
         LogTools.info("Slack notifications are disabled");
         LogTools.info("To use slack notifications, edit the values in " + slackSettingsFile.getAbsolutePath());
      }
   }

   @Override
   public void logSessionReport(Announcement announcement, LogReport logReport)
   {
      SlackLogMessage slackLogMessage = new SlackLogMessage();
      slackLogMessage.setLogDirectory("LOG DIR");
      System.out.println(slackLogMessage.toJson());

      // TODO: send log report to slack
   }

   private class SlackLogSettings
   {
      private final File settingsFile;
      private String slackWebhookURL;
      private String slackChannelName; // e.g. "#robot-log-report"

      public SlackLogSettings(File settingsFile)
      {
         this.settingsFile = settingsFile;
         this.slackWebhookURL = "";
         this.slackChannelName = "";
      }

      public boolean loaded()
      {
         return !slackWebhookURL.isBlank() && !slackChannelName.isBlank();
      }

      public synchronized void save() throws IOException
      {
         FileTools.ensureFileExists(settingsFile.toPath());

         Properties properties = new Properties();
         properties.setProperty("slack-webhook-url", slackWebhookURL);
         properties.setProperty("slack-channel-name", slackChannelName);

         try (FileOutputStream outputStream = new FileOutputStream(settingsFile))
         {
            properties.store(outputStream, null);
         }
      }

      public synchronized void load() throws IOException
      {
         FileTools.ensureFileExists(settingsFile.toPath());

         Properties properties = new Properties();

         try (FileInputStream inputStream = new FileInputStream(settingsFile))
         {
            properties.load(inputStream);
         }

         try
         {
            slackWebhookURL = properties.getProperty("slack-webhook-url").toString();
            slackChannelName = properties.getProperty("slack-channel-name").toString();
         }
         catch (Exception e)
         {
            // If we get a NPE on the toString(), we should delete and recreate the file with the original values
            // This can also handle any number parsing exceptions if we add number properties
            if (settingsFile.delete())
            {
               save();
            }
         }
      }
   }

   private class SlackLogMessage
   {
      private String logDirectory;

      public void setLogDirectory(String logDirectory)
      {
         this.logDirectory = logDirectory;
      }

      /**
       * Format the human message
       *
       * @return the message
       */
      public String getMessage()
      {
         StringBuilder stringBuilder = new StringBuilder();

         stringBuilder.append("A new robot data log has been generated.");
         stringBuilder.append(" ");
         stringBuilder.append(logDirectory);

         return stringBuilder.toString();
      }

      /**
       * Format the message to send to Slack in JSON
       *
       * @return the Slack-compatible JSON
       */
      public String toJson()
      {
         ObjectMapper mapper = new ObjectMapper();
         ObjectNode rootNode = mapper.createObjectNode();
         rootNode.put("channel", slackLogSettings.slackWebhookURL);
         rootNode.put("text", getMessage());
         String jsonString;
         try
         {
            jsonString = mapper.writeValueAsString(rootNode);
         }
         catch (JsonProcessingException e)
         {
            jsonString = "";
            LogTools.warn("Unable to format Slack JSON message");
         }
         return jsonString;
      }
   }
}
