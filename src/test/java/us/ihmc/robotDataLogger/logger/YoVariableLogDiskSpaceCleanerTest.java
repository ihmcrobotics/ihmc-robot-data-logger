package us.ihmc.robotDataLogger.logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class YoVariableLogDiskSpaceCleanerTest
{
   private static final long GB = 1024L * 1024L * 1024L;
   private static final long LARGE_DRIVE_SIZE = 1000 * GB;

   @Test
   void ignoresDiskCleanupWhenDriveIsSmallerThanMinimumThreshold(@TempDir Path root) throws IOException
   {
      Path log = createFakeLog(root, "20240101_000000_log", "20240101_000000");

      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB, (p) -> 100 * GB);

      assertTrue(Files.exists(log), "Logs should not be deleted when drive capacity is below the minimum threshold");
   }

   @Test
   void deletesOldestLogsUntilThresholdIsMet(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path middle = createFakeLog(root, "20240601_000000_middle", "20240601_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // Below threshold, then still below after freeing the oldest log, then above threshold after freeing the middle log
      Deque<Long> usableSpaceSequence = new ArrayDeque<>(java.util.List.of(100 * GB, 200 * GB, 600 * GB));
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> usableSpaceSequence.removeFirst(), (p) -> LARGE_DRIVE_SIZE);

      assertFalse(Files.exists(oldest), "Oldest log should have been deleted first");
      assertFalse(Files.exists(middle), "Middle log should have been deleted second");
      assertTrue(Files.exists(newest), "Newest log should be left alone once threshold is met");
   }

   @Test
   void doesNothingWhenAlreadyAboveThreshold(@TempDir Path root) throws IOException
   {
      Path onlyLog = createFakeLog(root, "20240101_000000_log", "20240101_000000");

      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 600 * GB, (p) -> LARGE_DRIVE_SIZE);

      assertTrue(Files.exists(onlyLog), "No log should be deleted when usable space is already above threshold");
   }

   @Test
   void stopsWithoutLoopingForeverWhenNoLogsAreLeftToDelete(@TempDir Path root) throws IOException
   {
      Path inProgressLog = createFakeLog(root, ".20240101_000000_inProgress", "20240101_000000");

      // Always below threshold - the only dot-prefixed log was just created, so it's too recent to be
      // assumed abandoned and there is nothing else safe to delete
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB, (p) -> LARGE_DRIVE_SIZE);

      assertTrue(Files.exists(inProgressLog), "A recent in-progress (dot-prefixed) log must never be deleted");
   }

   @Test
   void deletesStaleInProgressLogsAsLastResortWhenNoFinishedLogsRemain(@TempDir Path root) throws IOException
   {
      Path staleInProgressLog = createFakeLog(root, ".20240101_000000_crashed", "20240101_000000");
      backdateDirectory(staleInProgressLog, Duration.ofMinutes(30));

      // Always below threshold, and no finished logs exist - only a stale "." log left over from a crash
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB, (p) -> LARGE_DRIVE_SIZE);

      assertFalse(Files.exists(staleInProgressLog), "A stale in-progress log should be deleted as a last resort once no finished logs remain");
   }

   @Test
   void criticalCheckDeletesOldestLogWhenBelowPercentageThreshold(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // 0.5% usable out of a 1000 GB drive is below the 1% threshold, then 5% after the oldest log is freed
      Deque<Long> usableSpaceSequence = new ArrayDeque<>(java.util.List.of(5 * GB, 50 * GB));
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileCriticallyLowOnSpace(root, (p) -> usableSpaceSequence.removeFirst(), (p) -> LARGE_DRIVE_SIZE);

      assertFalse(Files.exists(oldest), "Oldest log should be deleted once usable space drops below 1% of total capacity");
      assertTrue(Files.exists(newest), "Newest log should be left alone once usable space is back above the percentage threshold");
   }

   @Test
   void criticalCheckDoesNothingWhenAlreadyAbovePercentageThreshold(@TempDir Path root) throws IOException
   {
      Path onlyLog = createFakeLog(root, "20240101_000000_log", "20240101_000000");

      // 50% usable is well above the 1% threshold
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileCriticallyLowOnSpace(root, (p) -> 500 * GB, (p) -> LARGE_DRIVE_SIZE);

      assertTrue(Files.exists(onlyLog), "No log should be deleted when usable space is already above the percentage threshold");
   }

   @Test
   void criticalCheckDeletesOldestLogsUntilPercentageThresholdIsMet(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path middle = createFakeLog(root, "20240601_000000_middle", "20240601_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // Expressed as multiples of the real threshold (not hardcoded GB numbers) so this test keeps testing
      // the right thing - and doesn't just silently pass or fail for the wrong reason - if
      // MINIMUM_FREE_SPACE_PERCENTAGE is ever changed.
      Deque<Long> usableSpaceSequence = new ArrayDeque<>(java.util.List.of(percentageThresholdInBytes(0.5),
                                                                           percentageThresholdInBytes(0.8),
                                                                           percentageThresholdInBytes(5.0)));
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileCriticallyLowOnSpace(root, (p) -> usableSpaceSequence.removeFirst(), (p) -> LARGE_DRIVE_SIZE);

      assertFalse(Files.exists(oldest), "Oldest log should have been deleted first");
      assertFalse(Files.exists(middle), "Middle log should have been deleted second");
      assertTrue(Files.exists(newest), "Newest log should be left alone once the percentage threshold is met");
   }

   @Test
   void runningBothChecksConcurrentlyDoesNotThrow(@TempDir Path root) throws Exception
   {
      int logCount = 50;
      for (int i = 0; i < logCount; i++)
      {
         String timestamp = String.format("202401%02d_%02d0000", (i % 28) + 1, i % 24);
         createFakeLog(root, timestamp + "_log" + i, timestamp);
      }

      // Both checks always see usable space below their own threshold, so every invocation races to find
      // and delete the oldest remaining log - exactly the scenario the shared lock needs to serialize.
      ExecutorService executor = Executors.newFixedThreadPool(8);
      try
      {
         List<Future<?>> futures = new ArrayList<>();
         for (int i = 0; i < logCount; i++)
         {
            futures.add(executor.submit(() ->
                                        {
                                           YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB, (p) -> LARGE_DRIVE_SIZE);
                                           return null;
                                        }));
            futures.add(executor.submit(() ->
                                        {
                                           // Half the real threshold - always below it, however MINIMUM_FREE_SPACE_PERCENTAGE is configured.
                                           YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileCriticallyLowOnSpace(root,
                                                                                                                   (p) -> percentageThresholdInBytes(0.5),
                                                                                                                   (p) -> LARGE_DRIVE_SIZE);
                                           return null;
                                        }));
         }

         assertDoesNotThrow(() ->
                            {
                               for (Future<?> future : futures)
                               {
                                  future.get(30, TimeUnit.SECONDS);
                               }
                            }, "Running both checks concurrently on the same log directory should never throw");
      }
      finally
      {
         executor.shutdownNow();
      }
   }

   @Test
   void abortsIfDeletingALogDoesNotActuallyFreeSpace(@TempDir Path root) throws IOException
   {
      Path oldest = createFakeLog(root, "20240101_000000_oldest", "20240101_000000");
      Path newest = createFakeLog(root, "20241201_000000_newest", "20241201_000000");

      // Usable space never improves, no matter how much gets deleted
      YoVariableLogDiskSpaceCleaner.deleteOldestLogsWhileLowOnSpace(root, (p) -> 100 * GB, (p) -> LARGE_DRIVE_SIZE);

      assertFalse(Files.exists(oldest), "Oldest log should still be deleted once");
      assertTrue(Files.exists(newest), "Newest log should be left alone once the safety valve trips after no progress is made");
   }

   /**
    * Computes a fake usable-space value as a multiple of the real {@code MINIMUM_FREE_SPACE_PERCENTAGE}
    * threshold (of a {@link #LARGE_DRIVE_SIZE} drive), e.g. {@code fractionOfThreshold} of 0.5 is always
    * below the threshold and 5.0 is always above it - regardless of what that threshold is currently set
    * to. Keeps percentage-based tests tied to the real constant instead of hardcoded GB numbers that would
    * silently stop meaning what the test claims if the threshold is ever changed.
    */
   private static long percentageThresholdInBytes(double fractionOfThreshold)
   {
      return (long) (LARGE_DRIVE_SIZE * (YoVariableLogDiskSpaceCleaner.MINIMUM_FREE_SPACE_PERCENTAGE / 100.0) * fractionOfThreshold);
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
