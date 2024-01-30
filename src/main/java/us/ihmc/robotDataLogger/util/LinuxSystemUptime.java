package us.ihmc.robotDataLogger.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;

public final class LinuxSystemUptime
{
   private static long systemUptimeSecondsAtJVMStart;

   static
   {
      OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

      if (operatingSystemMXBean.getName().equals("Linux"))
      {
         File unixUptime = new File("/proc/uptime");
         if (unixUptime.exists())
         {
            try
            {
               String systemUptimeSecondsString = FileUtils.readFileToString(unixUptime, StandardCharsets.UTF_8).split("\\.")[0];
               systemUptimeSecondsAtJVMStart = Long.parseLong(systemUptimeSecondsString);
            }
            catch (NumberFormatException | IOException e)
            {
               e.printStackTrace();
            }
         }
      }
   }

   public static long getSystemUptimeSecondsAtJVMStart()
   {
      return systemUptimeSecondsAtJVMStart;
   }
}
