package us.ihmc.robotDataLogger.logger;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;

import java.io.IOException;

/**
 * Identical to {@link YoVariableLoggerDispatcher}, except it automatically shuts down after
 * {@value #RUN_DURATION_SECONDS} seconds instead of running until killed. Handy for short,
 * one-off test runs of the logging pipeline.
 */
public class OneMinuteLogger extends YoVariableLoggerDispatcher
{
   private static final long RUN_DURATION_SECONDS = 60;

   public OneMinuteLogger(YoVariableLoggerOptions options) throws IOException
   {
      super(options);
   }

   @Override
   protected void waitForShutdown()
   {
      LogTools.info("Running for {} seconds before automatically shutting down", RUN_DURATION_SECONDS);
      ThreadTools.sleep((long) Conversions.secondsToMilliseconds(RUN_DURATION_SECONDS));
      LogTools.info("Time limit reached, shutting down");
      System.exit(0);
   }

   public static void main(String[] args) throws IOException, InterruptedException
   {
      YoVariableLoggerOptions options = YoVariableLoggerOptions.parse(args);
      new OneMinuteLogger(options);
   }
}
