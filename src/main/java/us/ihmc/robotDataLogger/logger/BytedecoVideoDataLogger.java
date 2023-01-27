package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

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
   private static boolean WRITTEN_TO_TIMESTAMP = false;

   final private static int WEBCAM_DEVICE_INDEX = 0;
   final private static int FRAME_RATE = 60;
   final int captureWidth = 1280;
   final int captureHeight = 720;
//   String filename;// = "C:/Users/nkitchel/robotLogs/BytedecoVideos/bytedecoLogVideo.mov";
   OpenCVFrameGrabber grabber;
   CanvasFrame cFrame;

   public static ArrayList<Long> timestampList = new ArrayList<>();

   private final int decklink;
   private final YoVariableLoggerOptions options;
   private FFmpegFrameRecorder recorder;

   private final CircularLongMap circularLongMap = new CircularLongMap(10000);

   private static FileWriter timestampWriter;

   private int frame;

   private volatile long lastFrameTimestamp = 0;

   public BytedecoVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      decklink = decklinkID;
      this.options = options;
//      this.filename = name + "_Video.mov";

      createCaptureInterface();
   }

   private void createCaptureInterface() throws FrameGrabber.Exception
   {
      File timestampFile = new File(timestampData);
      File videoCaptureFile = new File(videoFile);

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264:
         case AV_CODEC_ID_MJPEG:
            grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX);
            cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);
            recorder.setVideoOption("crf", "1");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setFormat("mov");
            recorder.setFrameRate(120);
            break;
         default:
            throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);

         ThreadTools.startAThread(() ->
         {
            try
            {
               startCapture();
            } catch (FFmpegFrameRecorder.Exception | FrameGrabber.Exception e)
            {
               throw new RuntimeException(e);
            }
         }, "Capture");
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
      ThreadTools.startAThread(null, "Capture");
      long startTime = 0;
      grabber.start();
      recorder.start();

      int i = 0;
      while (recorder != null)
      {

         Frame capturedFrame;

         if ((capturedFrame = grabber.grab()) != null)
         {
            LogTools.info(i);

            if (cFrame.isVisible())
            {
               cFrame.showImage(capturedFrame);
            }

            if (startTime == 0)
            {
               startTime = System.currentTimeMillis();
            }

            long videoTS = 1000 * (System.currentTimeMillis() - startTime);

            if (videoTS > recorder.getTimestamp())
            {
               System.out.println("Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));

               // We tell the recorder to write this frame at this timestamp
               recorder.setTimestamp(videoTS);
            }

            recordTimestampToArray(System.nanoTime(), i + 1);

            recorder.record(capturedFrame);
         }
         else
         {
            LogTools.info("Captured frame is null");
         }

         i++;
      }

      ThreadTools.join();
   }

   public static void recordTimestampToArray(long robotTimestamp, int index)
   {
      timestampList.add(robotTimestamp);
      timestampList.add(index * 1001L);
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
            recorder.flush();
            recorder.stop();
            grabber.stop();
            LogTools.info("Writing Timestamp");
            if (!WRITTEN_TO_TIMESTAMP)
            {
               writingToTimestamp();
            }
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

   public static void writingToTimestamp() throws IOException
   {
      timestampWriter.write("1\n");
      timestampWriter.write("60000\n");
      for (int i = 0; i < timestampList.size() - 1; i++)
      {
         timestampWriter.write(timestampList.get(i) + " " + timestampList.get(i + 1) + "\n");
         i++;
      }
      WRITTEN_TO_TIMESTAMP = true;
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

         long robotTimestamp = circularLongMap.getValue(true, hardwareTime);

//         try
//         {
//            if (frame == 0)
//            {
//               timestampWriter.write(timeScaleNumerator + "\n");
//               timestampWriter.write(timeScaleDenumerator + "\n");
//            }
//            timestampWriter.write(robotTimestamp + " " + pts + "\n");
//
//            lastFrameTimestamp = System.nanoTime();
//         }
//         catch (IOException e)
//         {
//            e.printStackTrace();
//         }
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
