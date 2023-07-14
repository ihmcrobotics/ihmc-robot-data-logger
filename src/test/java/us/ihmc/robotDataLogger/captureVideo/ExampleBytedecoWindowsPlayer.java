package us.ihmc.robotDataLogger.captureVideo;

import gnu.trove.list.array.TLongArrayList;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;


import java.io.*;

/**
 * This example class plays back a video recorded with Bytedeco from a decklink capture card
 * Doesn't matter if the video was recorded with SDI or HDMI this will work for either
 * It's important to note that the playback for the video has to use the same method that was done when
 * recording the original video or playback will be messed up.
 * TODO: For some reason the play back frames are way brighter then the video, figure out why
 */
public class ExampleBytedecoWindowsPlayer
{
//   public static String videoPath = "ihmc-robot-data-logger/out/windowsBytedeco_Video.mov";
//   public static String timestampPath = "ihmc-robot-data-logger/out/windowsBytedeco_Timestamps.dat";

   private static final String windowsBytedeco = "/windowsBytedeco";
   private static final String directoryPath = "C:/Users/robot/robotLogs/";
   private static final String logName = "20230714_170820_TestServer";

   // For testing things recorded from the logger
   public static String videoPath;
   public static String timestampPath;

   private static final TLongArrayList robotTimestamps = new TLongArrayList();
   private static final TLongArrayList ptsTimestamps = new TLongArrayList();

   // A really nice hardware accelerated component for our preview...
   public static final CanvasFrame cFrame = new CanvasFrame("Frames", CanvasFrame.getDefaultGamma());

   public static void main(String[] args) throws IOException
   {
      videoPath =  directoryPath + logName + windowsBytedeco + "_Video.mov";
      timestampPath = directoryPath + logName + windowsBytedeco + "_Timestamps.dat";

      // Read the timestamp file
      BufferedReader reader = new BufferedReader(new FileReader(timestampPath));

      String line;

      reader.readLine();
      reader.readLine();
      while ((line = reader.readLine()) != null)
      {
         String[] stamps = line.split("\\s");
         long robotStamp = Long.parseLong(stamps[0]);
         long pts = Long.parseLong(stamps[1]);

         robotTimestamps.add(robotStamp);
         ptsTimestamps.add(pts);
      }

      try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath))
      {
         grabber.start();

         for (int i = 0; i < robotTimestamps.size() - 1; i++)
         {
            grabber.setTimestamp(ptsTimestamps.get(i));
            Frame frame = grabber.grabFrame();

            if (cFrame.isVisible())
               cFrame.showImage(frame);
         }

         grabber.stop();

      }
      catch (Exception e)
      {
         e.printStackTrace();
      }

      cFrame.dispose();
      reader.close();
   }
}