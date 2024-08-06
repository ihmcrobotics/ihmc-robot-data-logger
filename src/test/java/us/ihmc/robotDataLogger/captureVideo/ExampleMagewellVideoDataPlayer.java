package us.ihmc.robotDataLogger.captureVideo;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serial;
import java.util.Arrays;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import gnu.trove.list.array.TLongArrayList;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import us.ihmc.codecs.generated.YUVPicture;
import us.ihmc.codecs.generated.YUVPicture.YUVSubsamplingType;
import us.ihmc.codecs.yuv.YUVPictureConverter;
import us.ihmc.robotDataLogger.Camera;
import us.ihmc.robotDataLogger.logger.MagewellDemuxer;

public class ExampleMagewellVideoDataPlayer
{
   private final boolean interlaced;

   private long[] robotTimestamps;
   private long[] videoTimestamps;

   private final MagewellDemuxer magewellDemuxer;

   private final HideableMediaFrame viewer;

   private final YUVPictureConverter converter = new YUVPictureConverter();

   private int currentlyShowingIndex = 0;
   private long currentlyShowingRobottimestamp = 0;
   private long upcomingRobottimestamp = 0;

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

      File timestampFile = new File(dataDirectory, camera.getTimestampFileAsString());

      parseTimestampData(timestampFile);

      magewellDemuxer = new MagewellDemuxer(dataDirectory, camera);

      viewer = new HideableMediaFrame(camera.getNameAsString(), magewellDemuxer.getImageWidth(), magewellDemuxer.getImageHeight());
   }

   public synchronized void showVideoFrame(long timestamp)
   {
      if (timestamp >= currentlyShowingRobottimestamp && timestamp < upcomingRobottimestamp)
      {
         return;
      }

      long previousTimestamp = videoTimestamps[currentlyShowingIndex];

      long videoTimestamp;
      if (robotTimestamps.length > currentlyShowingIndex + 1 && robotTimestamps[currentlyShowingIndex + 1] == timestamp)
      {
         currentlyShowingIndex++;
         videoTimestamp = videoTimestamps[currentlyShowingIndex];
         currentlyShowingRobottimestamp = robotTimestamps[currentlyShowingIndex];
      }
      else
      {
         videoTimestamp = getVideoTimestamp(timestamp);
      }

      if (currentlyShowingIndex + 1 < robotTimestamps.length)
      {
         upcomingRobottimestamp = robotTimestamps[currentlyShowingIndex + 1];
      }
      else
      {
         upcomingRobottimestamp = currentlyShowingRobottimestamp;
      }

      if (previousTimestamp == videoTimestamp)
      {
         return;
      }

      magewellDemuxer.seekToPTS(videoTimestamp);
      Frame nextFrame = magewellDemuxer.getNextFrame();
      viewer.update(convertFrameToYUVPicture(nextFrame));
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

   private long getVideoTimestamp(long timestamp)
   {
      currentlyShowingIndex = Arrays.binarySearch(robotTimestamps, timestamp);

      if (currentlyShowingIndex < 0)
      {
         int nextIndex = -currentlyShowingIndex + 1;
         if ((nextIndex < robotTimestamps.length) && (Math.abs(robotTimestamps[-currentlyShowingIndex] - timestamp) > Math.abs(robotTimestamps[nextIndex])))
         {
            currentlyShowingIndex = nextIndex;
         }
         else
         {
            currentlyShowingIndex = -currentlyShowingIndex;
         }
      }

      if (currentlyShowingIndex < 0)
         currentlyShowingIndex = 0;
      if (currentlyShowingIndex >= robotTimestamps.length)
         currentlyShowingIndex = robotTimestamps.length - 1;
      currentlyShowingRobottimestamp = robotTimestamps[currentlyShowingIndex];

      return videoTimestamps[currentlyShowingIndex];
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
      String videoName = "GroundCamera";
      camera.setTimestampFile(videoName + "_Timestamps.dat");
      camera.setVideoFile(videoName + "_Video.mov");

      File dataDirectory = new File("/home/ketchup/Videos/bloke/ExactCopyAt2800OfLogIAMTHEORIGINAL/");
      //      File dataDirectory = new File("/home/ketchup/workspaces/logger/repository-group/ihmc-robot-data-logger/out/");

      ExampleMagewellVideoDataPlayer player = new ExampleMagewellVideoDataPlayer(camera, dataDirectory, true);

      for (int i = 1; i < player.robotTimestamps.length; i++)
      {
         if (player.robotTimestamps[i - 1] > player.robotTimestamps[i])
         {
            System.out.println("Non-monotonic robot timestamps");
            System.out.println(player.robotTimestamps[i - 1]);
         }
         if (player.videoTimestamps[i - 1] >= player.videoTimestamps[i])
         {
            System.out.println("Non-monotonic video timestamps");
            System.out.println(player.videoTimestamps[i - 1]);
         }
      }

      player.viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      player.setVisible(true);

      for (int i = 1; i < player.robotTimestamps.length; i++)
      {
         player.showVideoFrame(player.robotTimestamps[i]);
         System.out.println(player.robotTimestamps[i]);
         // Play with the sleep to get things more realtime
         Thread.sleep(50);
      }
      System.out.println(player.robotTimestamps.length);
   }
}