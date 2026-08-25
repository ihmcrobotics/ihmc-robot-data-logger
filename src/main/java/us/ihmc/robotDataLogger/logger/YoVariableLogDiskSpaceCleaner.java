package us.ihmc.robotDataLogger.logger;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import us.ihmc.log.LogTools;

/**
 * Checks usable disk space where logs are stored, and deletes the oldest logs one at a time until
 * either enough space is free again or there are no more logs left to delete.
 */
public class YoVariableLogDiskSpaceCleaner
{
   @FunctionalInterface
   interface TotalSpaceProvider
   {
      long getTotalSpaceInBytes(Path root) throws IOException;
   }

   private static final long BYTES_PER_GIGABYTE = 1024L * 1024L * 1024L;
   // Round up to 520 so anything that is 512 or smaller gets captured in the check
   private static final double MINIMUM_FREE_SPACE_GIGABYTES = 520.0;

   // Backstop for a single long-running session that slowly eats through the initial free-space margin.
   // 1.0 represents 1% and 100.0 represents 100%
   private static final double MINIMUM_FREE_SPACE_PERCENTAGE = 1.0;

   // A "." log directory is actively being written to by a live session. Only treat one as an abandoned crash
   // leftover - and safe to delete as a last resort - once it hasn't been touched for this long.
   private static final Duration STALE_IN_PROGRESS_LOG_AGE = Duration.ofMinutes(15);

   // Multiple controller sessions share the same log directory, and each has both a one-time check at
   // connection and a periodic check while it logs. Without this, two of those checks running at once
   // could both pick the same "oldest log" and race to delete it. Deletion runs one at a time process-wide.
   private static final Object DELETE_LOCK = new Object();

   public static long getTotalSpaceInBytes(Path root) throws IOException
   {
      FileStore store = Files.getFileStore(root);
      return store.getTotalSpace();
   }

   public static long getUsableSpaceInBytes(Path root) throws IOException
   {
      FileStore store = Files.getFileStore(root);
      return store.getUsableSpace();
   }

   public static void deleteOldestLogsWhileLowOnSpace(Path root) throws IOException
   {
      deleteOldestLogsWhileLowOnSpace(root, YoVariableLogDiskSpaceCleaner::getUsableSpaceInBytes, YoVariableLogDiskSpaceCleaner::getTotalSpaceInBytes);
   }

   /**
    * Same as {@link #deleteOldestLogsWhileLowOnSpace(Path)}, but takes a {@link UsableSpaceProvider} instead of always querying the real filesystem.
    * Lets tests simulate a full disk without needing to actually fill one.
    */
   static void deleteOldestLogsWhileLowOnSpace(Path root, UsableSpaceProvider usableSpaceProvider, TotalSpaceProvider totalSpaceProvider) throws IOException
   {
      long minFreeSpaceBytes = (long) (MINIMUM_FREE_SPACE_GIGABYTES * BYTES_PER_GIGABYTE);

      long totalSpace = totalSpaceProvider.getTotalSpaceInBytes(root);
      if (totalSpace < minFreeSpaceBytes)
      {
         LogTools.info("Disk space check ignored for " + root + ". Drive capacity is only " + toGigabytes(totalSpace)
                       + " GB, which is smaller than the configured minimum free space threshold of " + MINIMUM_FREE_SPACE_GIGABYTES + " GB.");
         return;
      }

      deleteOldestLogsWhileBelowThreshold(root, minFreeSpaceBytes, MINIMUM_FREE_SPACE_GIGABYTES + " GB", usableSpaceProvider);
   }

   /**
    * Backstop check meant to be run periodically (e.g. on a timer) rather than only once when a
    * controller connects. A session that logs for long enough can fill the disk well after the
    * {@link #deleteOldestLogsWhileLowOnSpace(Path)} check at session start already passed.
    */
   public static void deleteOldestLogsWhileCriticallyLowOnSpace(Path root) throws IOException
   {
      deleteOldestLogsWhileCriticallyLowOnSpace(root,
                                                YoVariableLogDiskSpaceCleaner::getUsableSpaceInBytes,
                                                YoVariableLogDiskSpaceCleaner::getTotalSpaceInBytes);
   }

   static void deleteOldestLogsWhileCriticallyLowOnSpace(Path root, UsableSpaceProvider usableSpaceProvider, TotalSpaceProvider totalSpaceProvider)
         throws IOException
   {
      long totalSpace = totalSpaceProvider.getTotalSpaceInBytes(root);
      long minFreeSpaceBytes = (long) (totalSpace * (MINIMUM_FREE_SPACE_PERCENTAGE / 100.0));

      deleteOldestLogsWhileBelowThreshold(root,
                                          minFreeSpaceBytes,
                                          MINIMUM_FREE_SPACE_PERCENTAGE + "% of " + toGigabytes(totalSpace) + " GB",
                                          usableSpaceProvider);
   }

   private static void deleteOldestLogsWhileBelowThreshold(Path root,
                                                           long minFreeSpaceBytes,
                                                           String thresholdDescription,
                                                           UsableSpaceProvider usableSpaceProvider) throws IOException
   {
      synchronized (DELETE_LOCK)
      {
         long usableSpace = usableSpaceProvider.getUsableSpaceInBytes(root);
         if (usableSpace >= minFreeSpaceBytes)
         {
            // Another thread may have already freed enough space while we were waiting for the lock.
            return;
         }

         LogTools.warn("Usable disk space in " + root + " is " + toGigabytes(usableSpace) + " GB, below threshold of " + thresholdDescription
                       + ". Deleting oldest logs to free space.");

         while (usableSpace < minFreeSpaceBytes)
         {
            Optional<LogAndTimestamp> oldestFinishedLog = findOldestFinishedLog(root);
            String deletedLogDescription;

            if (oldestFinishedLog.isPresent())
            {
               deletedLogDescription = oldestFinishedLog.get().toString();
               oldestFinishedLog.get().delete();
            }
            else
            {
               Optional<Path> oldestStaleInProgressLog = findOldestStaleInProgressLog(root);
               if (oldestStaleInProgressLog.isEmpty())
               {
                  LogTools.warn("Usable disk space in " + root + " is still " + toGigabytes(usableSpace)
                                + " GB, but there are no more logs left to delete. Proceeding anyway.");
                  break;
               }

               deletedLogDescription = oldestStaleInProgressLog.get().toString();
               LogTools.warn("No finished logs left to delete. Deleting " + deletedLogDescription + ", an in-progress log untouched for over "
                             + STALE_IN_PROGRESS_LOG_AGE.toMinutes() + " minutes and assumed abandoned after a crash.");
               deleteDirectory(oldestStaleInProgressLog.get());
            }

            long previousUsableSpace = usableSpace;
            usableSpace = usableSpaceProvider.getUsableSpaceInBytes(root);

            if (usableSpace <= previousUsableSpace)
            {
               LogTools.warn("Deleting " + deletedLogDescription + " did not free disk space (still " + toGigabytes(usableSpace)
                             + " GB). Aborting further deletion attempts.");
               break;
            }
         }

         LogTools.info("Free space after deleting is: " + toGigabytes(usableSpace) + " GB");
      }
   }

   private static Optional<LogAndTimestamp> findOldestFinishedLog(Path root) throws IOException
   {
      try (Stream<Path> paths = Files.walk(root))
      {
         return paths.filter((p) -> !p.getFileName().toString().startsWith("."))
                     .filter((p) -> Files.exists(p.resolve(YoVariableLoggerListener.propertyFile)))
                     .map((p) -> new LogAndTimestamp(p))
                     .min(Comparator.naturalOrder());
      }
   }

   private static Optional<Path> findOldestStaleInProgressLog(Path root) throws IOException
   {
      Instant staleCutoff = Instant.now().minus(STALE_IN_PROGRESS_LOG_AGE);
      try (Stream<Path> children = Files.list(root))
      {
         return children.filter(Files::isDirectory)
                        .filter((p) -> p.getFileName().toString().startsWith("."))
                        .map((p) -> new DirectoryActivity(p, getMostRecentModificationTime(p)))
                        .filter((activity) -> activity.lastModified().isBefore(staleCutoff))
                        .min(Comparator.comparing(DirectoryActivity::lastModified))
                        .map(DirectoryActivity::directory);
      }
   }

   // The directory entry itself isn't reliably updated when an already-open file inside it is written to,
   // so staleness is judged by the newest last-modified time of any file within it, not the directory's own.
   private static Instant getMostRecentModificationTime(Path directory)
   {
      try (Stream<Path> files = Files.walk(directory))
      {
         return files.filter(Files::isRegularFile)
                     .map(YoVariableLogDiskSpaceCleaner::getLastModifiedTimeOrEpoch)
                     .max(Comparator.naturalOrder())
                     .orElse(Instant.EPOCH);
      }
      catch (IOException e)
      {
         return Instant.EPOCH;
      }
   }

   private static Instant getLastModifiedTimeOrEpoch(Path file)
   {
      try
      {
         return Files.getLastModifiedTime(file).toInstant();
      }
      catch (IOException e)
      {
         return Instant.EPOCH;
      }
   }

   private static void deleteDirectory(Path directory)
   {
      LogTools.info("Deleting " + directory);
      try
      {
         FileUtils.deleteDirectory(directory.toFile());
      }
      catch (IOException e)
      {
         System.err.println("Cannot delete " + directory);
      }
   }

   private record DirectoryActivity(Path directory, Instant lastModified)
   {
   }

   @FunctionalInterface
   interface UsableSpaceProvider
   {
      long getUsableSpaceInBytes(Path root) throws IOException;
   }

   private static double toGigabytes(long bytes)
   {
      return bytes / (double) BYTES_PER_GIGABYTE;
   }
}
