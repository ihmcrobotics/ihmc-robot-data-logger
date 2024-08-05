package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.bytedeco.ffmpeg.global.avutil;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

public class MagewellVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{
   private final YoVariableLoggerOptions options;

   private OpenCVFrameGrabber grabber;
   private FFmpegFrameRecorder recorder;
   private FileWriter timestampWriter;

   private int framesReceivedFromCameraCounter;
   private int timeStampFromControllerCounter;
   private final int deviceNumber;
   private volatile long latestTimeStampFromController = 0;

   public MagewellVideoDataLogger(String name, String captureType, File logPath, LogProperties logProperties, int deviceNumber, YoVariableLoggerOptions options)
         throws IOException
   {
      super(logPath, captureType, logProperties, name);
      this.deviceNumber = deviceNumber;
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

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264, AV_CODEC_ID_MJPEG ->
         {
            grabber = new OpenCVFrameGrabber(deviceNumber);
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);

            recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);

            // These settings have the best performance (H264 is a bad setting because of slicing)
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
                                        LogTools.error("Last frame is bad for {} but who cares, shutting down gracefully because of threading", deviceNumber);
                                     }
                                  }, "MagewellCapture");
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
         LogTools.info("Cannot start capture interface, timestamp file might already exist");
         e.printStackTrace();
      }
   }

   public void startCapture() throws FFmpegFrameRecorder.Exception, FrameGrabber.Exception
   {
      grabber.start();
      recorder.start();

      long startTime = System.currentTimeMillis();
      while (!recorder.isCloseOutputStream())
      {
         Frame capturedFrame;

         if ((capturedFrame = grabber.grab()) != null)
         {
            // Ensure the video timestamp is ahead of the record's current timestamp
            long videoTS = 1000 * (System.currentTimeMillis() - startTime);
            if (videoTS > recorder.getTimestamp())
            {
               // We tell the recorder to write this frame at this timestamp
               recorder.setTimestamp(videoTS);
            }

            // This is where a frame is record, and we then need to store the timestamps, so they are synced
            recorder.record(capturedFrame);
            receivedFrameAtTime(System.nanoTime(), recorder.getTimestamp(), 1, 60000);
         }
         else
         {
            LogTools.warn("Captured frame is null, rest in peace");
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

   /**
    * This receives a new timestamp from the controller. When we receive a new frame, we want to get the latest timestamp from the controller and store it with
    * the timestamp for the frame.
    * This way when looking at the log we have each camera frame (timestamp) synced to the latest controller timestamp, so they match.
    *
    * @param latestTimeStampFromController is the latest timestamp from the controller
    */
   @Override
   public void timestampChanged(long latestTimeStampFromController)
   {
      if (recorder != null)
      {
         // Update the latest timestamp from the controller
         // Note: we don't always get the timestamps on time, because of networking and such, we need to account for that when saving the frame
         this.latestTimeStampFromController = latestTimeStampFromController;
         if (timeStampFromControllerCounter == 5000)
         {
            timeStampFromControllerCounter = 0;
            LogTools.warn("For Device: {} From Controller (latestTimeStampFromController)={}", this.deviceNumber, this.latestTimeStampFromController);
         }

         timeStampFromControllerCounter++;
      }
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#close()
    */
   @Override
   public void close()
   {
      LogTools.info("Attempting to Stop video...");
      if (recorder != null)
      {
         try
         {
            LogTools.info("Stopping capture for {}, closing output stream of recorder, closing timestamp file... (Don't panic)", deviceNumber);
            recorder.setCloseOutputStream(true);
            recorder.flush();
            recorder.stop();
            grabber.stop();

            timestampWriter.close();
            System.out.println("Whew we did it! Done");
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
   public void receivedFrameAtTime(long hardwareTime, long recorderTimeStamp, long timeScaleNumerator, long timeScaleDenumerator)
   {
      // This prevents us from logging frames at the beginning of a log when things are just starting up
      if (latestTimeStampFromController != 0)
      {
         long controllerTimeStamp = this.latestTimeStampFromController;

         // TODO check for duplicate timestamps from the controller, and interpolate to a reasonable guess of what the controller time might be
         // Could check the last values from controller and see on average how much time goes in between them, and then add that to get the expected
         // that we want to record with.
         if (framesReceivedFromCameraCounter % 500 == 0)
         {
            LogTools.info("----- Saving the current frame at the current controller timestamp -----");
            LogTools.info("Camera Device Number: {}, at Frame: {},", deviceNumber, framesReceivedFromCameraCounter);
            LogTools.info("latestTimeStampFromController={}, recorderTimeStamp={}", controllerTimeStamp, recorderTimeStamp);
         }
         try
         {
            if (framesReceivedFromCameraCounter == 0)
            {
               timestampWriter.write(timeScaleNumerator + "\n");
               timestampWriter.write(timeScaleDenumerator + "\n");
            }
            timestampWriter.write(controllerTimeStamp + " " + recorderTimeStamp + "\n");
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         ++framesReceivedFromCameraCounter;
      }
   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return recorder.getTimestamp();
   }
}
