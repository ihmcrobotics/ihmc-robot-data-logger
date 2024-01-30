package us.ihmc.robotDataLogger.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;

public final class LinuxSystemUptime
{
   private static long systemUptimeAtJVMStartInSeconds;

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
               systemUptimeAtJVMStartInSeconds = Long.parseLong(systemUptimeSecondsString);
            }
            catch (NumberFormatException | IOException e)
            {
               e.printStackTrace();
            }
         }
      }
   }

   public static long getSystemUptimeAtJVMStartInSeconds()
   {
      return systemUptimeAtJVMStartInSeconds;
   }
}
