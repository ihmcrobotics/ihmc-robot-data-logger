package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.javacv.Frame;
import us.ihmc.robotDataLogger.Camera;
import us.ihmc.robotDataLogger.logger.MagewellDemuxer;

import java.io.File;

/**
 * This class allows the user to specify a file and see if when requesting a specific frame we are able to receive that frame or if the value is noll
 * The file for the video will be wrong so make sure to update it with the location of the video you want to test
 */
public class MagewellDemuxerFrameExistsTest
{
   public static void main(String[] args)
   {
      File videoFolder = new File("/home/ketchup/robotLogs/Copy me hehe/20240806_145159_NadiaControllerFactory/");
      Camera camera = new Camera();
      camera.setName("test");
      camera.setInterlaced(false);
      camera.setTimestampFile("GroundCamera_Timestamps.dat");
      camera.setVideoFile("GroundCamera_Video.mov");

      try
      {
         File videoFile = new File(videoFolder, "PoleCamera_Video.mov");
         MagewellDemuxer demuxer = new MagewellDemuxer(videoFile);
         demuxer.seekToPTS(16983333); // Seek to start
         Frame frame = demuxer.getNextFrame();

         if (frame != null)
         {
            System.out.println("Frame retrieved successfully:" + frame.timestamp);
         }
         else
         {
            System.out.println("Failed to retrieve frame.");
         }

         // Get a bunch more frames to see how things look
         for (int i = 0; i < 10; i++)
         {
            Frame frame2 = demuxer.getNextFrame();

            if (frame2 != null)
            {
               System.out.println("Frame retrieved successfully:" + frame2.timestamp);
            }
            else
            {
               System.out.println("Failed to retrieve frame.");
            }
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }
}