package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import us.ihmc.commons.Conversions;
import us.ihmc.javadecklink.Capture;
import us.ihmc.javadecklink.Capture.CodecID;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;

public class BlackmagicVideoDataLogger extends VideoDataLoggerInterface
{
   /**
    * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
    */

   private static int decklink;
   private final YoVariableLoggerOptions options;
   private Capture capture;

   private static FileWriter timestampWriter;

   private static int frame;

   private static volatile long lastFrameTimestamp = 0;

   public final TimestampAdjuster timestampAdjuster;

   public BlackmagicVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      decklink = decklinkID;
      this.options = options;

      createCaptureInterface();
      timestampAdjuster = new TimestampAdjuster();
   }

   private void createCaptureInterface()
   {
      File timestampFile = new File(timestampData);

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264 ->
         {
            capture = new Capture(timestampAdjuster, CodecID.AV_CODEC_ID_H264);
            capture.setOption("g", "1");
            capture.setOption("crf", String.valueOf(options.getCrf()));
            capture.setOption("profile", "high");
            capture.setOption("coder", "vlc");
         }
         case AV_CODEC_ID_MJPEG ->
         {
            capture = new Capture(timestampAdjuster, CodecID.AV_CODEC_ID_MJPEG);
            capture.setMJPEGQuality(options.getVideoQuality());
         }
         default -> throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);
         capture.startCapture(videoFile, decklink);
      }
      catch (IOException e)
      {
         capture = null;
         if (timestampWriter != null)
         {
            try
            {
               timestampWriter.close();
               timestampFile.delete();
            }
            catch (IOException e1)
            {
               LogTools.info("Could not delete timestamp files");
            }
         }
         timestampWriter = null;
         LogTools.info("Cannot start capture interface");
         e.printStackTrace();
      }
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#restart()
    */
   @Override
   public void restart() throws IOException
   {
      close();
      removeLogFiles();
      createCaptureInterface();
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#timestampChanged(long)
    */
   @Override
   public void timestampChanged(long controllerTimestamp)
   {
      if (capture != null)
      {
         long hardwareTimestamp = capture.getHardwareTime();
         if (hardwareTimestamp != -1)
         {
            timestampAdjuster.addToCircularMap(hardwareTimestamp, controllerTimestamp);
         }
      }
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#close()
    */
   @Override
   public void close()
   {
      LogTools.info("Signalling recorder to shut down.");
      if (capture != null)
      {
         try
         {
            LogTools.info("Stopping capture.");
            capture.stopCapture();
            LogTools.info("Closing writer.");
            timestampWriter.close();
            LogTools.info("Done.");
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         capture = null;
         timestampWriter = null;
      }
   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return lastFrameTimestamp;
   }

   public static class TimestampAdjuster implements CaptureHandler
   {
      private final CircularLongMap circularLongMap = new CircularLongMap(10000);

      private long previousControllerTimestamp = 0;

      private long previousRobotTimestamp = 0;
      private long averageDelay;
      private long totalDelay = 0;
      private long missedFrames = 1;

      public TimestampAdjuster()
      {}

      public void addToCircularMap(long hardwareTimestamp, long controllerTimestamp)
      {
         circularLongMap.insert(hardwareTimestamp, controllerTimestamp);
         calculateExpectedDelay(controllerTimestamp);
      }

      private void calculateExpectedDelay(long controllerTimestamp)
      {
         long currentDelay = Math.abs(previousControllerTimestamp - controllerTimestamp);

         totalDelay += currentDelay;
         averageDelay = totalDelay / circularLongMap.size();
         previousControllerTimestamp = controllerTimestamp;
      }

      @Override
      public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
      {
         if (circularLongMap.size() > 0)
         {
            if (frame % 600 == 0)
            {
               double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
               System.out.println("[Decklink " + decklink + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
            }

            long currentRobotTimestamp = circularLongMap.getValue(true, hardwareTime);

            // Checks if we have gotten a duplicate out of the circularLongMap
            if (previousRobotTimestamp == currentRobotTimestamp)
            {
               currentRobotTimestamp += averageDelay * missedFrames;
               missedFrames++;
            }
            else
            {
               previousRobotTimestamp = currentRobotTimestamp;
               missedFrames = 1;
            }

            try
            {
               if (frame == 0)
               {
                  timestampWriter.write(timeScaleNumerator + "\n");
                  timestampWriter.write(timeScaleDenumerator + "\n");
               }

               if (missedFrames < 100)
                  timestampWriter.write(currentRobotTimestamp + " " + pts + "\n");

               lastFrameTimestamp = System.nanoTime();
            }
            catch (IOException e)
            {
               e.printStackTrace();
            }
            ++frame;
         }
      }
   }
}
