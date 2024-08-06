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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;
import us.ihmc.codecs.generated.YUVPicture;
import us.ihmc.codecs.yuv.YUVPictureConverter;
import us.ihmc.robotDataLogger.Camera;
import us.ihmc.robotDataLogger.logger.MagewellDemuxer;
import us.ihmc.robotDataLogger.logger.MagewellMuxer;

public class ExampleMagewellVideoDataPlayer
{
   private final boolean hasTimebase;
   private final boolean interlaced;

   private long[] robotTimestamps;
   private long[] videoTimestamps;

   private long bmdTimeBaseNum;
   private long bmdTimeBaseDen;

   private final MagewellDemuxer magewellDemuxer;
   private final ImageView viewer = new ImageView();

//   private final HideableMediaFrame viewer;

   private final YUVPictureConverter converter = new YUVPictureConverter();

   private int currentlyShowingIndex = 0;
   private long currentlyShowingRobottimestamp = 0;
   private long upcomingRobottimestamp = 0;

   public ExampleMagewellVideoDataPlayer(Camera camera, File dataDirectory, boolean hasTimeBase) throws IOException
   {
      this.interlaced = camera.getInterlaced();
      this.hasTimebase = hasTimeBase;

      if (!hasTimebase)
      {
         System.err.println("Video data is using timestamps instead of frame numbers. Falling back to seeking based on timestamp.");
      }

      File videoFile = new File(dataDirectory, camera.getVideoFileAsString());

      if (!videoFile.exists())
      {
         throw new IOException("Cannot find video: " + videoFile);
      }

      File timestampFile = new File(dataDirectory, camera.getTimestampFileAsString());

      parseTimestampData(timestampFile);

      magewellDemuxer = new MagewellDemuxer(videoFile, camera);

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

      try
      {
         magewellDemuxer.seekToPTS(videoTimestamp);
         Frame nextFrame = magewellDemuxer.getNextFrame();
         viewer.update(convertFrameToWritableImage(nextFrame));
      }
      catch (IOException e)
      {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   /**
    * This class converts a {@link Frame} to a {@link WritableImage} in order to be displayed correctly in JavaFX.
    *
    * @param frameToConvert is the next frame we want to visualize so we convert it to be compatible with JavaFX
    * @return {@link WritableImage}
    */
   public WritableImage convertFrameToWritableImage(Frame frameToConvert)
   {
      Image currentImage;

      if (frameToConvert == null)
      {
         return null;
      }

      try (JavaFXFrameConverter frameConverter = new JavaFXFrameConverter())
      {
         currentImage = frameConverter.convert(frameToConvert);
      }
      WritableImage writableImage = new WritableImage((int) currentImage.getWidth(), (int) currentImage.getHeight());
      PixelReader pixelReader = currentImage.getPixelReader();
      PixelWriter pixelWriter = writableImage.getPixelWriter();

      for (int y = 0; y < currentImage.getHeight(); y++)
      {
         for (int x = 0; x < currentImage.getWidth(); x++)
         {
            pixelWriter.setArgb(x, y, pixelReader.getArgb(x, y));
         }
      }

      return writableImage;
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

      long videoTimestamp = videoTimestamps[currentlyShowingIndex];

//      if (hasTimebase)
//      {
//         videoTimestamp = (videoTimestamp * bmdTimeBaseNum * demuxer.getTimescale()) / (bmdTimeBaseDen);
//      }

      return videoTimestamp;
   }

   private void parseTimestampData(File timestampFile) throws IOException
   {
      try (BufferedReader reader = new BufferedReader(new FileReader(timestampFile)))
      {

         String line;
         if (hasTimebase)
         {
            if ((line = reader.readLine()) != null)
            {
               bmdTimeBaseNum = Long.parseLong(line);
            }
            else
            {
               throw new IOException("Cannot read numerator");
            }

            if ((line = reader.readLine()) != null)
            {
               bmdTimeBaseDen = Long.parseLong(line);
            }
            else
            {
               throw new IOException("Cannot read denumerator");
            }
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

      //      File dataDirectory = new File("/home/ketchup/workspaces/logger/repository-group/ihmc-robot-data-logger/out/");
      File dataDirectory = new File("/home/ketchup/robotLogs/12/");
      //      File dataDirectory = new File("C:/Users/nkitchel/Workspaces/Security-Camera/repository-group/ihmc-robot-data-logger/src/test/resources");
      //      File dataDirectory = new File("C:/Users/nkitchel/Documents/security-camera/repository-group/ihmc-video-codecs/src/test/resources/");

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