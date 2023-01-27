package us.ihmc.robotDataLogger.logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.ihmc.commons.nio.FileTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.listeners.LogAnnouncementListener;

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

public class SlackLogAnnouncementListener implements LogAnnouncementListener
{
   private static final String SLACK_SETTINGS_FILE_NAME = "slackSettings.properties";
   private final SlackLogSettings slackLogSettings;

   private enum LogEventType
   {
      STARTED, FINISHED;
   }

   public SlackLogAnnouncementListener(YoVariableLoggerOptions options)
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
   public void logSessionCameOnline(Announcement announcement)
   {
      SlackLogMessage slackLogMessage = new SlackLogMessage(slackLogSettings, LogEventType.STARTED);
      slackLogMessage.setLogDirectoryName(announcement.getTimestampNameAsString());
      publishSlackLogMessage(slackLogMessage, announcement);
   }

   @Override
   public void logSessionWentOffline(Announcement announcement)
   {
      SlackLogMessage slackLogMessage = new SlackLogMessage(slackLogSettings, LogEventType.FINISHED);
      slackLogMessage.setLogDirectoryName(announcement.getTimestampNameAsString());
      publishSlackLogMessage(slackLogMessage, announcement);
   }

   public CompletableFuture<Boolean> publishSlackLogMessage(SlackLogMessage slackLogMessage, Announcement announcement)
   {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
                                       .uri(URI.create(slackLogSettings.slackWebhookURL))
                                       .POST(HttpRequest.BodyPublishers.ofString(slackLogMessage.toJson()))
                                       .build();
      return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
               boolean success = response.statusCode() == 200;
               if (success)
                  LogTools.info("Pushed log report notification to Slack " + announcement.getTimestampNameAsString());
               else
                  LogTools.info("Unable to POST to Slack webhook. Check your " + SLACK_SETTINGS_FILE_NAME);
               return success;
            });
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
      private LogEventType eventType;

      public SlackLogMessage(SlackLogSettings slackLogSettings, LogEventType eventType)
      {
         this.slackLogSettings = slackLogSettings;
         this.eventType = eventType;
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
         switch (eventType) {
            case STARTED -> stringBuilder.append("A new robot data log has been started.");
            case FINISHED -> stringBuilder.append("A new robot data log has finished.");
         }
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
