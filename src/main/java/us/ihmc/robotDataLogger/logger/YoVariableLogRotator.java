package us.ihmc.robotDataLogger.logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import us.ihmc.log.LogTools;

/**
 * Class to rotate logs to avoid infinite accumulation
 *
 * @author jesper
 */
public class YoVariableLogRotator
{
   public static void rotate(Path root, int logsToKeep) throws IOException
   {
      LogTools.info("Rotating logs in " + root + ". Keeping " + logsToKeep + " logs");
      // Find every finished log directory (skip in-progress "." dirs), sort newest first, and delete everything past the keep count
      try (Stream<Path> paths = Files.walk(root))
      {
         paths.filter((p) -> !p.getFileName().toString().startsWith("."))
              .filter((p) -> Files.exists(p.resolve(YoVariableLoggerListener.propertyFile)))
              .map(LogAndTimestamp::new)
              .sorted(Comparator.reverseOrder())
              .skip(logsToKeep)
              .forEach(LogAndTimestamp::delete);
      }
   }

   public static void main(String[] args) throws IOException
   {
      Path dir = Paths.get(System.getProperty("user.home"), "robotLogs");
      YoVariableLogRotator.rotate(dir, 6);
   }
}
