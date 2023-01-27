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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class LogReportSlackListener extends LogReportListener
{
   private static final String SLACK_SETTINGS_FILE_NAME = "slackSettings.properties";
   private final SlackLogSettings slackLogSettings;

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
      SlackLogMessage slackLogMessage = new SlackLogMessage(slackLogSettings);
      slackLogMessage.setLogDirectoryName(announcement.getTimestampNameAsString());
      publishSlackLogMessage(slackLogMessage).thenAccept(success -> {
         if (success)
            LogTools.info("Pushed log report notification to Slack " + announcement.getTimestampNameAsString());
         else
            LogTools.info("Unable to POST to Slack webhook. Check your " + SLACK_SETTINGS_FILE_NAME);
      });
   }

   public CompletableFuture<Boolean> publishSlackLogMessage(SlackLogMessage slackLogMessage)
   {
      HttpClient client = HttpClient.newHttpClient();
      System.out.println(slackLogSettings.slackWebhookURL);
      System.out.println(slackLogMessage.toJson());

      HttpRequest request = HttpRequest.newBuilder()
                                       .uri(URI.create(slackLogSettings.slackWebhookURL))
                                       .POST(HttpRequest.BodyPublishers.ofString(slackLogMessage.toJson()))
                                       .build();
      return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(stringHttpResponse -> stringHttpResponse.statusCode() == 200);
   }

   private static class SlackLogSettings
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

   private static class SlackLogMessage
   {
      private final SlackLogSettings slackLogSettings;
      private String logDirectoryName;

      public SlackLogMessage(SlackLogSettings slackLogSettings)
      {
         this.slackLogSettings = slackLogSettings;
      }

      public void setLogDirectoryName(String logDirectoryName)
      {
         this.logDirectoryName = logDirectoryName;
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
         stringBuilder.append("\n");
         stringBuilder.append(logDirectoryName);

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
         rootNode.put("channel", slackLogSettings.slackChannelName);
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
