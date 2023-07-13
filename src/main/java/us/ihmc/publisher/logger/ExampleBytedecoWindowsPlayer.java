package us.ihmc.publisher.logger;

import gnu.trove.list.array.TLongArrayList;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import us.ihmc.commons.thread.ThreadTools;

import java.io.*;

public class ExampleBytedecoWindowsPlayer
{
   public static String videoPath = "ihmc-robot-data-logger/out/windowsBytedecoVideo.mov";
   public static final String timestampPath = "ihmc-robot-data-logger/out/windowsBytedecoTimestamps.dat";

   private static final TLongArrayList robotTimestamps = new TLongArrayList();
   private static final TLongArrayList ptsTimestamps = new TLongArrayList();

   // A really nice hardware accelerated component for our preview...
   public static final CanvasFrame cFrame = new CanvasFrame("Frames", CanvasFrame.getDefaultGamma());

   public static void main(String[] args) throws IOException
   {
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
            ThreadTools.sleepSeconds(0.01);
            grabber.setTimestamp(ptsTimestamps.get(i));
            Frame frame = grabber.grabFrame();

            if (cFrame.isVisible())
            {
               cFrame.showImage(frame);
            }
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