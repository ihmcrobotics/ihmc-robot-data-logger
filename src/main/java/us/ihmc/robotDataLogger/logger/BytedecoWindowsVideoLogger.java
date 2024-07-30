package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.bytedeco.ffmpeg.global.avutil;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

public class BytedecoWindowsVideoLogger extends VideoDataLoggerInterface implements CaptureHandler
{
   private int frame;
   private final CircularLongMap circularLongMap = new CircularLongMap(10000);
   private static FileWriter timestampWriter;

   private final int deviceNumber;
   private final YoVariableLoggerOptions options;
   private OpenCVFrameGrabber grabber;
   private FFmpegFrameRecorder recorder;

   private volatile long lastFrameTimestamp = 0;
   private int timestampCounter;

   public BytedecoWindowsVideoLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      deviceNumber = decklinkID;
      this.options = options;

      createCaptureInterface();
   }

   private void createCaptureInterface()
   {
      File timestampFile = new File(timestampData);
      File videoCaptureFile = new File(videoFile);

      // This is the resolution of the video, overrides camera
      int captureWidth = 1920;
      int captureHeight = 1080;

      switch (options.getVideoCodec()) {
         case AV_CODEC_ID_H264, AV_CODEC_ID_MJPEG ->
         {
            grabber = new OpenCVFrameGrabber(deviceNumber);
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);

            recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);

            // Trying these settings for now (H264 is a bad setting because of slicing)
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setFormat("mov");
            // This video codec is deprecated, so in order to use it without errors we have to set the pixel format and strictly allow FFMPEG to use it
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setVideoOption("strict", "-2");
            // Frame rate of video recordings
            recorder.setFrameRate(60);

         }
         default -> throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);

         ThreadTools.startAThread(() ->
         {
            try
            {
               startCapture();
            }
            catch (FFmpegFrameRecorder.Exception | FrameGrabber.Exception e)
            {
               LogTools.error("Last frame is bad, shutting down gracefully because of threading");
            }
         }, "BytedecoWindowsCapture");
      }
      catch (IOException e)
      {
         recorder = null;

         if (timestampWriter != null)
         {
            try
            {
               timestampWriter.close();
               timestampFile.delete();
            }
            catch (IOException ignored)
            {
            }
         }

         timestampWriter = null;
         LogTools.info("Cannot start capture interface");
         e.printStackTrace();
      }
   }

   public void startCapture() throws FFmpegFrameRecorder.Exception, FrameGrabber.Exception
   {
      grabber.start();
      recorder.start();

      long startTime = 0;
      while (!recorder.isCloseOutputStream())
      {
         Frame capturedFrame;

         if ((capturedFrame = grabber.grab()) != null)
         {
            if (startTime == 0)
               startTime = System.currentTimeMillis();

            long videoTS = 1000 * (System.currentTimeMillis() - startTime);

            if (videoTS > recorder.getTimestamp())
            {
               // We tell the recorder to write this frame at this timestamp
               recorder.setTimestamp(videoTS);
            }

            recorder.record(capturedFrame);
            // System.nanoTime() represents the controllerTimestamp in this example since its fake
            receivedFrameAtTime(System.nanoTime(), recorder.getTimestamp(), 1, 60000);
         }
         else
         {
            LogTools.info("Captured frame is null");
         }
      }

      ThreadTools.join();
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
   public void timestampChanged(long newTimestamp)
   {
      if (recorder != null)
      {
         long hardwareTimestamp = getHardwareTimestamp();

         if (hardwareTimestamp > 0)
         {
            if (timestampCounter == 500)
            {
               timestampCounter = 0;
               LogTools.info("hardwareTimestamp={}, newTimestamp={}", hardwareTimestamp, newTimestamp);
            }

            timestampCounter++;
            circularLongMap.insert(hardwareTimestamp, newTimestamp);
         }
      }
   }

   private long getHardwareTimestamp()
   {

      return System.nanoTime();
//      return Math.round(recorder.getFrameNumber() * 1000000000L / recorder.getFrameRate());
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#close()
    */
   @Override
   public void close()
   {
      LogTools.info("Signalling recorder to shut down.");
      if (recorder != null)
      {
         try
         {
            LogTools.info("Stopping capture.");
            recorder.setCloseOutputStream(true);
            recorder.flush();
            recorder.stop();
            grabber.stop();

            LogTools.info("Closing writer.");
            timestampWriter.close();
            LogTools.info("Done.");
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         recorder = null;
         timestampWriter = null;
      }

   }

   @Override
   public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
   {
      if (circularLongMap.size() > 0)
      {
         if (frame % 600 == 0)
         {
            double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
            System.out.println("[Decklink " + deviceNumber + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
         }

         long robotTimestamp = circularLongMap.getValue(true, hardwareTime);

         try
         {
            if (frame == 0)
            {
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
