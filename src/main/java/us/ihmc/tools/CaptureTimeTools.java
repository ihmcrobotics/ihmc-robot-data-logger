package us.ihmc.tools;

public class CaptureTimeTools
{
   /**
    * This takes the current time in milliseconds, and returns the seconds that have passed from the start time.
    * @param currentTimeInMilliSeconds is the current time that you want to compare too
    * @param startTime is the start time from when capture started
    * @return the time in seconds that has passed since the start time
    */
   public static long timeSinceStartedCaptureInSeconds(long currentTimeInMilliSeconds, long startTime)
   {
      return 1000 * (currentTimeInMilliSeconds - startTime);
   }
}
