package us.ihmc.robotDataLogger.logger;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import us.ihmc.codecs.builder.MP4MJPEGMovieBuilder;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.robotDataLogger.guiRecorder.GUICaptureHandler;
import us.ihmc.robotDataLogger.guiRecorder.GUICaptureReceiver;

public class NetworkStreamVideoDataLogger extends VideoDataLoggerInterface implements GUICaptureHandler
{
   private final GUICaptureReceiver client;

   private MP4MJPEGMovieBuilder builder;
   private PrintStream timestampStream;
   private int dts = 0;

   private volatile long timestamp = 0;

   private volatile long lastFrameTimestamp = 0;

   public NetworkStreamVideoDataLogger(File logPath, LogProperties logProperties, String modelName, int domainId, String topicName) throws IOException
   {
      super(logPath, logProperties, topicName);

      client = new GUICaptureReceiver(domainId, modelName, topicName, this);
      client.start();
   }

   @Override
   public void timestampChanged(long newTimestamp)
   {
      timestamp = newTimestamp;
   }

   @Override
   public void restart() throws IOException
   {
      try
      {
         if (builder != null)
         {
            builder.close();
         }
         if (timestampStream != null)
         {
            timestampStream.close();
         }
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      builder = null;
      timestampStream = null;
      removeLogFiles();
   }

   @Override
   public void close()
   {
      client.close();
      try
      {
         if (builder != null)
         {
            builder.close();
         }
         if (timestampStream != null)
         {
            timestampStream.close();
         }
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      builder = null;
      timestampStream = null;
   }

   @Override
   public void receivedFrame(ByteBuffer buffer)
   {
      if (builder == null)
      {
         try
         {
            // Decode the first image using ImageIO, to get the dimensions
            ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer.array());
            BufferedImage img = ImageIO.read(inputStream);
            if (img == null)
            {
               System.err.println("Cannot decode image");
               return;
            }
            File videoFileFile = new File(videoFile);
            builder = new MP4MJPEGMovieBuilder(videoFileFile, img.getWidth(), img.getHeight(), 10, 90);
            File timestampFile = new File(timestampData);
            timestampStream = new PrintStream(timestampFile);
            timestampStream.println("1");
            timestampStream.println("10");
            buffer.clear();

            dts = 0;

            lastFrameTimestamp = System.nanoTime();
         }
         catch (IOException e)
         {
            e.printStackTrace();
            return;
         }

      }
      try
      {
         builder.encodeFrame(buffer);
         timestampStream.println(timestamp + " " + dts);
         dts++;
      }
      catch (IOException e)
      {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return lastFrameTimestamp;
   }

}
