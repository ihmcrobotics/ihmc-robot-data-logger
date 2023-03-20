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

public class BlackmagicVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{

   /**
    * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
    */

   private final int decklink;
   private final YoVariableLoggerOptions options;
   private Capture capture;

   private final CircularLongMap circularLongMap = new CircularLongMap(10000);

   private FileWriter timestampWriter;

   private int frame;

   private long latestRobotTimestamp;
   private volatile long lastFrameTimestamp = 0;

   public BlackmagicVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      decklink = decklinkID;
      this.options = options;

      createCaptureInterface();
   }

   private void createCaptureInterface()
   {
      File timestampFile = new File(timestampData);

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264:
            capture = new Capture(this, CodecID.AV_CODEC_ID_H264);
            capture.setOption("g", "1");
            capture.setOption("crf", String.valueOf(options.getCrf()));
            capture.setOption("profile", "high");
            capture.setOption("coder", "vlc");
            break;
         case AV_CODEC_ID_MJPEG:
            capture = new Capture(this, CodecID.AV_CODEC_ID_MJPEG);
            capture.setMJPEGQuality(options.getVideoQuality());
            break;
         default:
            throw new RuntimeException();
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
   @Override //This is called by the robotstuff, and then it gets the camera timestamp that was saved lasttime, so
   // the capture.getHardwareTime() gets the last timestamp but isn't runnin on the same machine so it can be called whenever
   // I may not be able to get a gstreamer timestamp when ever so I may need to map it to System.nanotime() and them map that
   // to getHardwaretime(), regardless, play around with this and test it out.
   // getHardwareTime calls the native method that goes and gets the last timestamp
   public void timestampChanged(long robotTimestamp)
   {
      // This is called by the robot computers I think and just maps the robot timestamp to the cameratimestamp
      if (capture != null)
      {
         long cameraTimestamp = capture.getHardwareTime();
         if (cameraTimestamp != -1)
         {
            System.out.println("New method");
            latestRobotTimestamp = robotTimestamp;
            // This maps the robottimestamp to the camera timestamp, so they are synced
            circularLongMap.insert(cameraTimestamp, robotTimestamp);
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

   //This is called by the logger, and it writes the timestamp and synces the frame to the timestamp
   @Override
   public void receivedFrameAtTime(long cameraTimestamp, long pts, long timeScaleNumerator, long timeScaleDenumerator)
   {
      if (circularLongMap.size() > 0)
      {
         if (frame % 600 == 0)
         {
            double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - cameraTimestamp);
            System.out.println("[Decklink " + decklink + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
         }

         //Then we get the actual robot timestamp based on the camera timestamp that was saved.
         //These are run on different machines so the timeing is going to be different.
         // This is why the use of mapping is needed because the actual recording timestamp is done by the logger
//         long robotTimestamp = circularLongMap.getValue(true, cameraTimestamp);
         long robotTimestamp = latestRobotTimestamp;

         try
         {
            if (frame == 0)
            {
               System.out.println("Writing to file again");
               timestampWriter.write(timeScaleNumerator + "\n");
               timestampWriter.write(timeScaleDenumerator + "\n");
            }
            timestampWriter.write(robotTimestamp + " " + pts + "\n");

            lastFrameTimestamp = System.nanoTime();
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         ++frame;
      }

   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return lastFrameTimestamp;
   }

}
