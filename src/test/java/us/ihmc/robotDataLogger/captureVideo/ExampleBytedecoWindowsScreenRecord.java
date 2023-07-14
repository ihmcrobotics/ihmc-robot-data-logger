package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This example class records the screen and saves it to file, this can be played back with Bytedeco as well
 */
public class ExampleBytedecoWindowsScreenRecord
{
   private static long startTime = 0;

   public static String videoPath = "ihmc-robot-data-logger/out/windowsBytedecoScreenRecordingVideo.mov";
   public static final String timestampPath = "ihmc-robot-data-logger/out/windowsBytedecoScreenRecordingTimestamps.dat";
   private static FileWriter timestampWriter;

   public static File videoFile = new File(videoPath);
   public static File timestampFile = new File(timestampPath);
   public static void main(String[] args) throws IOException
   {

      try (FrameGrabber grabber = new FFmpegFrameGrabber("desktop"))
      {
         grabber.setFormat("gdigrab");
         grabber.setFrameRate(60);
         grabber.start();

         setupTimestampWriter();

         try(FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(videoFile, 1280, 720))
         {
            // Trying these settings for now (H264 is a bad setting because of slicing)
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
            recorder.setFormat("mov");
            recorder.setFrameRate(60);

            recorder.start();

            int timer = 0;
            Frame capturedFrame;

            while (timer < 50 && ((capturedFrame = grabber.grab()) != null))
            {
               // Keeps track of time for the recorder because often times it gets offset
               if (startTime == 0)
                  startTime = System.currentTimeMillis();

               long videoTS = 1000 * (System.currentTimeMillis() - startTime);

               if (videoTS > recorder.getTimestamp())
               {
                  if (timer % 5 == 0)
                     System.out.println("Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));

                  // We tell the recorder to write this frame at this timestamp
                  recorder.setTimestamp(videoTS);
               }

               recorder.record(capturedFrame);
               // System.nanoTime() represents the controllerTimestamp in this example since its fake
               writeTimestampToFile(System.nanoTime(), recorder.getTimestamp());

               timer++;
            }

            recorder.stop();
            grabber.stop();
            timestampWriter.close();
         }
      }
   }

   public static void setupTimestampWriter()
   {
      try
      {
         timestampWriter = new FileWriter(timestampFile);
         timestampWriter.write(1 + "\n");
         timestampWriter.write(60000 + "\n");
      }
      catch (IOException e)
      {
         LogTools.info("Didn't setup the timestamp file correctly");
      }
   }

   /**
    * Write the timestamp sent from the controller, and then we get the timestamp of the camera, and write both
    * of those to a file.
    */
   public static void writeTimestampToFile(long controllerTimestamp, long pts)
   {
      try
      {
         timestampWriter.write(controllerTimestamp + " " + pts + "\n");
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }
}