package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

public class BytedecoVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{

   /**
    * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
    */
   final private static int WEBCAM_DEVICE_INDEX = 0;
   final private static int FRAME_RATE = 60;
   final int captureWidth = 1280;
   final int captureHeight = 720;
   String filename = "C:/Users/nkitchel/robotLogs/BytedecoVideos/bytedecoLogVideo.mov";
   VideoInputFrameGrabber grabber;// = new VideoInputFrameGrabber(WEBCAM_DEVICE_INDEX);
   CanvasFrame cFrame;// = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());


   private final int decklink;
   private final YoVariableLoggerOptions options;
   private FFmpegFrameRecorder recorder;

   private final CircularLongMap circularLongMap = new CircularLongMap(10000);

   private FileWriter timestampWriter;

   private int frame;

   private volatile long lastFrameTimestamp = 0;

   public BytedecoVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      decklink = decklinkID;
      this.options = options;

      createCaptureInterface();
   }

   private void createCaptureInterface() throws FrameGrabber.Exception
   {
      File timestampFile = new File(timestampData);

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264:
         case AV_CODEC_ID_MJPEG:
            grabber = new VideoInputFrameGrabber(WEBCAM_DEVICE_INDEX);
            cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            grabber.start();
            recorder = new FFmpegFrameRecorder(filename, captureWidth, captureHeight);
            recorder.setVideoOption("crf", "1");
            recorder.setVideoOption("coder", "vlc");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoBitrate(8000000);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setFormat("mov");
            recorder.setFrameRate(FRAME_RATE);
            break;
         default:
            throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);
         recorder.start();

         ThreadTools.startAThread(this::startCapture, "Capture");
//         startCapture();
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
            catch (IOException e1)
            {
            }
         }
         timestampWriter = null;
         LogTools.info("Cannot start capture interface");
         e.printStackTrace();
      }
   }

   public void startCapture()
   {
      ThreadTools.startAThread(null, "Capture");

      int i = 0;
      while (recorder != null)
      {

         try
         {
            Frame capturedFrame;

            if ((capturedFrame = grabber.grab()) != null)
            {
               LogTools.info(i);

               if (cFrame.isVisible())
               {
                  cFrame.showImage(capturedFrame);
               }

               //            recorder.setTimestamp(newTimestamp);
               recorder.record(capturedFrame);
            }
            else
            {
               LogTools.info("Captured frame is null");
            }
         }
         catch (FrameGrabber.Exception | FFmpegFrameRecorder.Exception e)
         {
            throw new RuntimeException(e);
         }

         i++;
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
   public void timestampChanged(long newTimestamp)
   {
      LogTools.info("Running timestampChanged function");
      if (recorder != null)
      {
         long hardwareTimestamp = recorder.getTimestamp();

         if (hardwareTimestamp != -1)
         {
            circularLongMap.insert(hardwareTimestamp, newTimestamp);
            receivedFrameAtTime(hardwareTimestamp, newTimestamp, 1, 60000);
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
      if (recorder != null)
      {
         try
         {
            LogTools.info("Stopping capture.");
            cFrame.dispose();
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
      LogTools.info("Well receivedFrameAtTime could be often");
      if (circularLongMap.size() > 0)
      {
         if (frame % 600 == 0)
         {
            double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
            System.out.println("[Decklink " + decklink + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
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
      LogTools.info("Going inside getLastFrameReceivedTimestamp");
      return lastFrameTimestamp;
   }

}
