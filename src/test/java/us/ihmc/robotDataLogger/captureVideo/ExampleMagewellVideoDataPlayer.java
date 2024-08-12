package us.ihmc.robotDataLogger.captureVideo;

import gnu.trove.list.array.TLongArrayList;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import us.ihmc.codecs.generated.YUVPicture;
import us.ihmc.codecs.generated.YUVPicture.YUVSubsamplingType;
import us.ihmc.codecs.yuv.YUVPictureConverter;
import us.ihmc.robotDataLogger.Camera;
import us.ihmc.robotDataLogger.logger.MagewellDemuxer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serial;
import java.util.Arrays;

public class ExampleMagewellVideoDataPlayer
{
   private final boolean interlaced;
   private long[] robotTimestamps;
   private long[] videoTimestamps;

   private final MagewellDemuxer magewellDemuxer;
   private final HideableMediaFrame viewer;
   private final YUVPictureConverter converter = new YUVPictureConverter();

   /**
    * This class plays back a video recorded with Magewell, this is helpful for debugging information about the video.
    */
   public ExampleMagewellVideoDataPlayer(Camera camera, File dataDirectory, boolean hasTimeBase) throws IOException
   {
      this.interlaced = camera.getInterlaced();

      if (!hasTimeBase)
      {
         System.err.println("Video data is using timestamps instead of frame numbers. Falling back to seeking based on timestamp.");
      }

      if (!dataDirectory.exists())
      {
         throw new IOException("Cannot find video: " + dataDirectory);
      }

      File videoFile = new File(dataDirectory, camera.getVideoFileAsString());
      File timestampFile = new File(dataDirectory, camera.getTimestampFileAsString());

      parseTimestampData(timestampFile);

      magewellDemuxer = new MagewellDemuxer(videoFile);

      viewer = new HideableMediaFrame(camera.getNameAsString(), magewellDemuxer.getImageWidth(), magewellDemuxer.getImageHeight());
   }

   public synchronized void showVideoFrame(long timestamp)
   {
      long videoTimestamp = getVideoTimestampFromRobotTimestamp(timestamp);

      magewellDemuxer.seekToPTS(videoTimestamp);
      Frame nextFrame = magewellDemuxer.getNextFrame();
      if (nextFrame != null)
      {
         viewer.update(convertFrameToYUVPicture(nextFrame));
      }
   }

   private YUVPictureConverter convertedYUVPicture;
   private Java2DFrameConverter frameConverter;

   public YUVPicture convertFrameToYUVPicture(Frame frame)
   {
      if (convertedYUVPicture == null)
      {
         convertedYUVPicture = new YUVPictureConverter();
         frameConverter = new Java2DFrameConverter();
      }

      BufferedImage bufferedImage = frameConverter.getBufferedImage(frame);
      return convertedYUVPicture.fromBufferedImage(bufferedImage, YUVSubsamplingType.YUV420);
   }

   public void setVisible(boolean visible)
   {
      viewer.setVisible(visible);
   }

   /**
    * Searches the list of robotTimestamps for the value closest to queryRobotTimestamp and returns that index. Then sets videoTimestamp to
    * that index in oder to display the right frame.
    *
    * @param queryRobotTimestamp the value sent from the robot data in which we want to find the closest robotTimestamp in the instant file.
    * @return the videoTimestamp that matches the index of the closest robotTimestamp in our instant file.
    */
   public long getVideoTimestampFromRobotTimestamp(long queryRobotTimestamp)
   {
      int currentIndex = searchRobotTimestampsForIndex(queryRobotTimestamp);
      return videoTimestamps[currentIndex];
   }

   private int searchRobotTimestampsForIndex(long queryRobotTimestamp)
   {
      if (queryRobotTimestamp <= robotTimestamps[0])
         return 0;

      if (queryRobotTimestamp >= robotTimestamps[robotTimestamps.length - 1])
         return robotTimestamps.length - 1;

      int index = Arrays.binarySearch(robotTimestamps, queryRobotTimestamp);

      if (index < 0)
      {
         int nextIndex = -index - 1; // insertionPoint
         index = nextIndex;
      }

      return index;
   }

   private void parseTimestampData(File timestampFile) throws IOException
   {
      try (BufferedReader reader = new BufferedReader(new FileReader(timestampFile)))
      {

         String line;
         if ((reader.readLine()) != null)
         {
           // Reading first line of file
         }
         else
         {
            throw new IOException("Cannot read numerator");
         }

         if ((reader.readLine()) != null)
         {
            // Reading second line of file
         }
         else
         {
            throw new IOException("Cannot read denumerator");
         }

         TLongArrayList robotTimestamps = new TLongArrayList();
         TLongArrayList videoTimestamps = new TLongArrayList();
         while ((line = reader.readLine()) != null)
         {
            String[] stamps = line.split("\\s");
            long robotStamp = Long.parseLong(stamps[0]);
            long videoStamp = Long.parseLong(stamps[1]);

            if (interlaced)
            {
               videoStamp /= 2;
            }

            robotTimestamps.add(robotStamp);
            videoTimestamps.add(videoStamp);
         }

         this.robotTimestamps = robotTimestamps.toArray();
         this.videoTimestamps = videoTimestamps.toArray();
      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }

   private class HideableMediaFrame extends JFrame
   {
      @Serial
      private static final long serialVersionUID = -3494797002318746347L;
      final JLabel label = new JLabel();
      private BufferedImage img;
      private int width, height;

      public HideableMediaFrame(String name, int width, int height)
      {
         super(name);
         label.setPreferredSize(new Dimension(width, height));
         getContentPane().add(label);
         this.width = width;
         this.height = height;
         pack();
      }

      public void update(final YUVPicture nextFrame)
      {
         SwingUtilities.invokeLater(() ->
                                    {
                                       if (nextFrame == null)
                                          return;
                                       img = converter.toBufferedImage(nextFrame, img);
                                       nextFrame.delete();
                                       ImageIcon icon = new ImageIcon(img);
                                       label.setIcon(icon);

                                       if (img.getWidth() != width || img.getHeight() != height)
                                       {
                                          width = img.getWidth();
                                          height = img.getHeight();
                                          label.setPreferredSize(new Dimension(width, height));
                                          pack();
                                       }
                                    });
      }
   }

   public static void main(String[] args) throws IOException, InterruptedException
   {
      Camera camera = new Camera();
      camera.setName("test");
      camera.setInterlaced(false);
      String videoName = "PoleCamera";
      camera.setTimestampFile(videoName + "_Timestamps.dat");
      camera.setVideoFile(videoName + "_Video.mov");

      File dataDirectory = new File("/home/ketchup/robotLogs/20240808_093303_SCS2AvatarSimulationFactory/");
      //      File dataDirectory = new File("/home/ketchup/workspaces/logger/repository-group/ihmc-robot-data-logger/out/");

      ExampleMagewellVideoDataPlayer player = new ExampleMagewellVideoDataPlayer(camera, dataDirectory, true);

      player.viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      player.setVisible(true);

      for (int i = 1; i < player.robotTimestamps.length; i++)
      {
         player.showVideoFrame(player.robotTimestamps[i]);
      }

      System.out.println(player.robotTimestamps.length);
   }
}