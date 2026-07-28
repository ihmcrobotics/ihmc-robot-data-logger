package us.ihmc.robotDataLogger.logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

public class YoVariableLogDiskSpaceCleanerTest
{
   private static final long GB = 1024L * 1024L * 1024L;

   @Test
   void deletesOldestLogsUntilThresholdIsMet(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path middle = createFakeLog(root, "20240601_000000_middle", "20240601_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // Below threshold, then still below after freeing the oldest log, then above threshold after freeing the middle log
      Deque<Long> usableSpaceSequence = new ArrayDeque<>(java.util.List.of(400 * GB, 450 * GB, 600 * GB));
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> usableSpaceSequence.poll());

      assertFalse(Files.exists(oldest), "Oldest log should have been deleted first");
      assertFalse(Files.exists(middle), "Middle log should have been deleted second");
      assertTrue(Files.exists(newest), "Newest log should be left alone once threshold is met");
   }

   @Test
   void doesNothingWhenAlreadyAboveThreshold(@TempDir Path root) throws IOException
   {
      Path onlyLog = createFakeLog(root, "20240101_000000_log", "20240101_000000");

      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 600 * GB);

      assertTrue(Files.exists(onlyLog), "No log should be deleted when usable space is already above threshold");
   }

   @Test
   void stopsWithoutLoopingForeverWhenNoLogsAreLeftToDelete(@TempDir Path root) throws IOException
   {
      Path inProgressLog = createFakeLog(root, ".20240101_000000_inProgress", "20240101_000000");

      // Always below threshold - the only dot-prefixed log was just created, so it's too recent to be
      // assumed abandoned and there is nothing else safe to delete
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB);

      assertTrue(Files.exists(inProgressLog), "A recent in-progress (dot-prefixed) log must never be deleted");
   }

   @Test
   void deletesStaleInProgressLogsAsLastResortWhenNoFinishedLogsRemain(@TempDir Path root) throws IOException
   {
      Path staleInProgressLog = createFakeLog(root, ".20240101_000000_crashed", "20240101_000000");
      backdateDirectory(staleInProgressLog, Duration.ofMinutes(30));

      // Always below threshold, and no finished logs exist - only a stale "." log left over from a crash
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB);

      assertFalse(Files.exists(staleInProgressLog), "A stale in-progress log should be deleted as a last resort once no finished logs remain");
   }

   @Test
   void abortsIfDeletingALogDoesNotActuallyFreeSpace(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // Usable space never improves, no matter how much gets deleted
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB);

      assertFalse(Files.exists(oldest), "Oldest log should still be deleted once");
      assertTrue(Files.exists(newest), "Newest log should be left alone once the safety valve trips after no progress is made");
   }

   private static Path createFakeLog(Path root, String directoryName, String timestamp) throws IOException
   {
      Path logDirectory = root.resolve(directoryName);
      Files.createDirectories(logDirectory);
      Files.writeString(logDirectory.resolve(YoVariableLoggerListener.propertyFile), """
            version=5.0
            name=%s
            timestamp=%s
            """.formatted(directoryName, timestamp));
      return logDirectory;
   }

   private static void backdateDirectory(Path directory, Duration age) throws IOException
   {
      FileTime backdated = FileTime.from(Instant.now().minus(age));
      try (Stream<Path> files = Files.walk(directory))
      {
         for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator)
         {
            Files.setLastModifiedTime(file, backdated);
         }
      }
   }
}
