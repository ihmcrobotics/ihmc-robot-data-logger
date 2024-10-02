package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.javacv.*;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.logger.MagewellMuxer;
import us.ihmc.tools.CaptureTimeTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static us.ihmc.robotDataLogger.logger.MagewellVideoDataLogger.*;

/**
 * This example class provides the basics for capturing a video using the ByteDeco JavaCV bindings
 * You need a Magewell capture card, and a camera attached on the other end for this to work
 * The camera settings get used for the framerate and the cable type.
 * This works on Ubuntu 22.04
 * This example works with either SDI or HDMI as the camera cable
 */

public class ExampleMagewellCapture
{
   private static final int DEVICE_INDEX = 0;

   private static final String ubuntuMagewell = "ubuntuMagewell";

   public static String videoPath;
   public static String timestampPath;
   private static FileWriter timestampWriter;

   private static MagewellMuxer magewellMuxer;

   public static File videoFile;
   public static File timestampFile;

   private static long timestampsWritten = 0;
   private static final int CAPTURE_TIME_DURATION = 10;

   public static void main(String[] args) throws InterruptedException
   {
      videoPath = "ihmc-robot-data-logger/out/" + ubuntuMagewell + "_Video.mov";
      timestampPath = "ihmc-robot-data-logger/out/" + ubuntuMagewell + ".dat";

      videoFile = new File(videoPath);
      timestampFile = new File(timestampPath);

      // Can try VideoInputFrameGrabber as well to see how that one works, and there is FFMPEGFrameGrabber
      try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(DEVICE_INDEX))
      {
         grabber.setImageWidth(CAPTURE_WIDTH);
         grabber.setImageHeight(CAPTURE_HEIGHT);
         grabber.setFrameRate(CAPTURE_FPS);
         grabber.start();

         setupTimestampWriter();

         magewellMuxer = new MagewellMuxer(videoFile, CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS, CAPTURE_TIME_DURATION);
         magewellMuxer.start();

         // A really nice hardware accelerated component for our preview...
         final CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

         Frame capturedFrame;
         long startTime = System.currentTimeMillis();

         ThreadTools.startAThread(ExampleMagewellCapture::keepTime, "MagewellCapture");

         // Loop to capture a video, will stop after iterations have been completed
         LogTools.info("Starting capture");
         while (!magewellMuxer.isCloseOutputStream() && ((capturedFrame = grabber.grabAtFrameRate()) != null))
         {
            long videoTimestamp = CaptureTimeTools.timeSinceStartedCaptureInSeconds(System.currentTimeMillis(), startTime);
            magewellMuxer.recordFrame(capturedFrame, videoTimestamp);

            // Shows the captured frame its currently recording
            if (cFrame.isVisible())
            {
               cFrame.showImage(capturedFrame);
            }

            // System.nanoTime() represents the controllerTimestamp in this example since its fake
            writeTimestampToFile(System.nanoTime(), magewellMuxer.getTimeStamp());
         }

         LogTools.info("Stopping Capture");
         LogTools.info("Number of timestamps is: " + timestampsWritten);
         LogTools.info("FPS was: " + timestampsWritten / CAPTURE_TIME_DURATION);

         cFrame.dispose();

         //Stop running the recorder and the frame grabber
         grabber.stop();
         timestampWriter.close();
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
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

   private static void keepTime()
   {
      // This allows the capture to run for a set amount of time, meaning after 10 seconds at 60 fps we should expect around 600 timestamps
      // to be in the file. This is a good check that the speed is fast enough
      ThreadTools.sleepSeconds(CAPTURE_TIME_DURATION);
      magewellMuxer.close();
   }

   /**
    * Write the timestamp sent from the controller, and then we get the timestamp of the camera, and write both
    * of those to a file.
    */
   public static void writeTimestampToFile(long controllerTimestamp, long pts)
   {
      try
      {
         timestampsWritten++;
         timestampWriter.write(controllerTimestamp + " " + pts + "\n");
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }
}