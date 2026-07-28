package us.ihmc.robotDataLogger.logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.io.FileUtils;

import us.ihmc.log.LogTools;

/**
 * A log directory paired with the timestamp it was recorded at, so logs can be sorted oldest/newest for rotation.
 */
class LogAndTimestamp implements Comparable<LogAndTimestamp>
{
   private static final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

   private final Path directory;
   private final LocalDateTime timestamp;

   public LogAndTimestamp(Path directory)
   {
      this.directory = directory;

      LocalDateTime timestamp = LocalDateTime.MIN;
      try
      {
         LogPropertiesReader reader = new LogPropertiesReader(directory.resolve(YoVariableLoggerListener.propertyFile).toFile());
         if (reader.getTimestampAsString().trim().isEmpty())
         {
            LogTools.warn("Empty timestamp for log in " + directory + ", assuming LocalDateTime.MIN");
         }
         else
         {
            timestamp = LocalDateTime.parse(reader.getTimestampAsString(), timestampFormat);
         }
      }
      catch (Exception e)
      {
         LogTools.warn("Could not parse timestamp for log in " + directory + ", assuming LocalDateTime.MIN");
      }
      this.timestamp = timestamp;
   }

   public void delete()
   {
      LogTools.info("Deleting " + this);
      try
      {
         FileUtils.deleteDirectory(directory.toFile());
      }
      catch (IOException e)
      {
         System.err.println("Cannot delete " + directory);
      }
   }

   @Override
   public int compareTo(LogAndTimestamp o)
   {
      return timestamp.compareTo(o.timestamp);
   }

   @Override
   public String toString()
   {
      return timestamp + ": " + directory.toString();
   }
}
