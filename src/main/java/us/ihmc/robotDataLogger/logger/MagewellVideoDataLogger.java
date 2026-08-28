package us.ihmc.robotDataLogger.logger;

import logger_msgs.LogProperties;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.tools.CaptureTimeTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MagewellVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{
   private static final long CAPTURE_THREAD_SHUTDOWN_TIMEOUT = 2000;

   private final YoVariableLoggerOptions options;

   private OpenCVFrameGrabber grabber;
   private FileWriter timestampWriter;
   private MagewellMuxer magewellMuxer;
   private Thread captureThread;

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
      int captureWidth = 1280;
      int captureHeight = 720;

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264, AV_CODEC_ID_MJPEG ->
         {
            grabber = new OpenCVFrameGrabber(deviceNumber);
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            grabber.setFrameRate(60);

            magewellMuxer = new MagewellMuxer(videoCaptureFile, captureWidth, captureHeight);
         }
         default -> throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);

         captureThread = ThreadTools.startAThread(() ->
                                                  {
                                                     try
                                                     {
                                                        startCapture();
                                                     }
                                                     catch (Exception e)
                                                     {
                                                        LogTools.error("Last frame is bad for {} but who cares, shutting down gracefully because of threading",
                                                                       deviceNumber);
                                                     }
                                                  }, "MagewellCapture");
      }
      catch (IOException e)
      {
         magewellMuxer = null;

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

   public void startCapture() throws Exception
   {
      try
      {
         grabber.start();
         magewellMuxer.start();

         timestampWriter.write(1 + "\n");
         timestampWriter.write(60 + "\n");

         long startTime = System.currentTimeMillis();
         Frame capturedFrame;
         while (!magewellMuxer.isClosed() && ((capturedFrame = grabber.grabAtFrameRate()) != null))
         {
            long videoTimestamp = CaptureTimeTools.timeSinceStartedCaptureInMicroseconds(System.currentTimeMillis(), startTime);
            magewellMuxer.recordFrame(capturedFrame, videoTimestamp);
            receivedFrameAtTime(System.nanoTime(), magewellMuxer.getTimeStamp(), 1, 60000);
         }
      }
      finally
      {
         // This exists to stop this threading bug: stopping these from another thread (e.g. close()) while this thread might still be inside
         // a native call using the same resources is a use-after-free race in the native layer.
         try
         {
            grabber.stop();
         }
         finally
         {
            magewellMuxer.stopRecording();
         }
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
      if (magewellMuxer != null)
      {
         // Update the latest timestamp from the controller
         // Note: we don't always get the timestamps on time, because of networking and such, we need to account for that when saving the frame
         this.latestTimeStampFromController = latestTimeStampFromController;
         if (timeStampFromControllerCounter == 20_000) // This only prints once every 10000 timestamps to not blow up the terminal
         {
            timeStampFromControllerCounter = 0;
            LogTools.warn("For camera: {}, from controller (latestTimeStampFromController)={}", this.deviceNumber, this.latestTimeStampFromController);
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
      if (magewellMuxer != null)
      {
         LogTools.info("Stopping capture for {}, closing output stream of recorder, closing timestamp file... (Don't panic)", deviceNumber);

         // Signals startCapture()'s loop to exit.
         magewellMuxer.close();

         if (captureThread != null)
         {
            try
            {
               captureThread.join(CAPTURE_THREAD_SHUTDOWN_TIMEOUT);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }

            if (captureThread.isAlive())
            {
               LogTools.error("MagewellCapture thread for device {} did not stop within {}ms, it is likely stuck, buckets",
                              deviceNumber,
                              CAPTURE_THREAD_SHUTDOWN_TIMEOUT);
            }

            captureThread = null;
         }

         try
         {
            timestampWriter.close();
            System.out.println("Whew we did it! Done");
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }

         magewellMuxer = null;
         timestampWriter = null;
      }
   }

   @Override
   public void receivedFrameAtTime(long hardwareTime, long recorderTimeStamp, long timeScaleNumerator, long timeScaleDenumerator)
   {
      ++framesReceivedFromCameraCounter;

      // TODO check for duplicate timestamps from the controller, and interpolate to a reasonable guess of what the controller time might be
      // Could check the last values from controller and see on average how much time goes in between them, and then add that to get the expected
      // that we want to record with.
      if (framesReceivedFromCameraCounter % 1200 == 0) // This only prints once every 1200 frames to not blow up the terminal
      {
         LogTools.info("--- Saving the current frame at the current controller timestamp ---");
         LogTools.info("Camera Number: {}, at Frame: {},", deviceNumber, framesReceivedFromCameraCounter);
         LogTools.info("latestTimeStampFromController={}, recorderTimeStamp={}", latestTimeStampFromController, recorderTimeStamp);
      }

      try
      {
         timestampWriter.write(latestTimeStampFromController + " " + recorderTimeStamp + "\n");
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return magewellMuxer.getTimeStamp();
   }
}
