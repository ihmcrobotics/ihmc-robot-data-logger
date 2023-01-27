package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

// There is currently no audio capture in this class
public class ScreenCapture
{
   public static ArrayList<Long> timestampList = new ArrayList<>();

   private static long startTime = 0;

   public static void main(String[] args) throws IOException, InterruptedException
   {

      try (FrameGrabber grabber = new FFmpegFrameGrabber("desktop"))
      {
         grabber.setFormat("gdigrab");
         grabber.setFrameRate(60);
         grabber.start();


//         public static File timestampFile = new File("C:/Users/nkitchel/Workspaces/Security-Camera/repository-group/ihmc-video-codecs/src/test/resources/testing.dat");

         //         String filename = "C:/Users/nadiaocu/eclipse-java-17-ws/repository-group/ihmc-video-codecs/src/test/resources/recordedVideo.mov";
         String filename = "C:/Users/nkitchel/Workspaces/Security-Camera/repository-group/ihmc-video-codecs/src/test/resources/screenRecording.mov";

         final CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

         try(FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filename, 1280, 720))
         {
            recorder.setVideoOption("crf", "1");
            recorder.setVideoOption("coder", "vlc");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoBitrate(8000000);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setFormat("mov");
            recorder.setFrameRate(90);

            recorder.start();

            int timer = 0;
            Frame capturedFrame;

            while (timer < 20 && ((capturedFrame = grabber.grab()) != null))
            {
               // Shows the captured frame its currently recording
//               if (cFrame.isVisible())
//               {
//                  cFrame.showImage(capturedFrame);
//               }

               // Keeps track of time for the recorder because often times it gets offset
               if (startTime == 0)
               {
                  startTime = System.currentTimeMillis();
               }

               long videoTS = 1000 * (System.currentTimeMillis() - startTime);

               if (videoTS > recorder.getTimestamp())
               {
                  System.out.println("Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));
//                  System.out.println(System.nanoTime());
//                  System.out.println(timer);
                  // We tell the recorder to write this frame at this timestamp
                  recorder.setTimestamp(videoTS);
               }

               recorder.record(capturedFrame);


               recordTimestampToArray(System.nanoTime(), timer);

               timer += 1;
            }

            cFrame.dispose();

            recorder.stop();

            grabber.stop();
         }
      }
      System.out.println("Printing timestamp file");
      Thread.sleep(2000);
      printArray();
   }

   public static void recordTimestampToArray(long robotTimestamp, int index)
   {
      timestampList.add(robotTimestamp);
      timestampList.add(index * 1001L);
   }

   public static void printArray()
   {
      System.out.println("1");
      System.out.println("60000");
      for (int i = 0; i < timestampList.size() - 1; i++)
      {
         System.out.println(timestampList.get(i) + " " + timestampList.get(i + 1));
         i++;
      }
   }
}