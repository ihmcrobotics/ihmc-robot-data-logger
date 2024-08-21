package us.ihmc.tools;

public class CaptureTimeTools
{
   public static long timeSinceStartedCaptureInSeconds(long milliseconds, long startTime)
   {
      return 1000 * milliseconds - startTime;
   }
}
