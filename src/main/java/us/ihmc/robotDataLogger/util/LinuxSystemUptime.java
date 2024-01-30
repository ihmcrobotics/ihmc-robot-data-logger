package us.ihmc.robotDataLogger.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;

/**
 * A utility class for retrieving the current system uptime on a Linux system.
 */
public final class LinuxSystemUptime
{
   /**
    * The result parsed from /proc/uptime
    */
   private static long systemUptimeQueryResult;
   /**
    * The time in milliseconds that /proc/uptime was queried
    */
   private static long systemUptimeQueryTimeMillis;

   static
   {
      OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

      if (operatingSystemMXBean.getName().equals("Linux"))
      {
         /*
            /proc/uptime

            Example output:
            713907.21 16441853.59

            The first value represents the total number of seconds the system has been up.
            The second value is the sum of how much time each core has spent idle, in seconds.
          */
         File unixUptime = new File("/proc/uptime");

         if (unixUptime.exists())
         {
            try
            {
               String systemUptimeSecondsString = FileUtils.readFileToString(unixUptime, StandardCharsets.UTF_8).split("\\.")[0];
               systemUptimeQueryResult = Long.parseLong(systemUptimeSecondsString);
               systemUptimeQueryTimeMillis = System.currentTimeMillis();
            }
            catch (NumberFormatException | IOException e)
            {
               e.printStackTrace();
            }
         }
      }
   }

   /**
    * Retrieve the current uptime of a Linux system
    *
    * @return the amount of seconds the Linux system has been online.
    * Equivalent to:
    * <pre>
    *    $ cat /proc/uptime | awk -F. '{print $1}'
    * </pre>
    */
   public static long getSystemUptime()
   {
      long now = System.currentTimeMillis();
      long durationSinceLastQuery = now - systemUptimeQueryTimeMillis;
      return systemUptimeQueryResult + (durationSinceLastQuery / 1000);
   }
}
