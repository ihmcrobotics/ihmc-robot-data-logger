package us.ihmc.robotDataLogger.logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;

import us.ihmc.log.LogTools;

/**
 * Class to rotate logs to avoid infinite accumulation
 * 
 * @author jesper
 */
public class YoVariableLogRotator
{
   private static final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

   public static void rotate(Path root, int logsToKeep) throws IOException
   {
      LogTools.info("Rotating logs in " + root + ". Keeping " + logsToKeep + " logs");
      Files.walk(root).filter((p) -> Files.exists(p.resolve(YoVariableLoggerListener.propertyFile))).map((p) -> new LogAndTimestamp(p))
           .sorted(Comparator.reverseOrder()).skip(logsToKeep).forEach((t) -> t.delete());

   }

   private static class LogAndTimestamp implements Comparable<LogAndTimestamp>
   {
      private final Path directory;
      private final LocalDateTime timestamp;

      public LogAndTimestamp(Path directory)
      {
         LogPropertiesReader reader = new LogPropertiesReader(directory.resolve(YoVariableLoggerListener.propertyFile).toFile());

         this.directory = directory;
         this.timestamp = LocalDateTime.parse(reader.getTimestampAsString(), timestampFormat);
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

   public static void main(String[] args) throws IOException
   {
      Path dir = Paths.get(System.getProperty("user.home"), "robotLogs");
      YoVariableLogRotator.rotate(dir, 6);
   }
}
