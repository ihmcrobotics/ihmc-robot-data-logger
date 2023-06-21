package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import us.ihmc.commons.Conversions;
import us.ihmc.javadecklink.Capture;
import us.ihmc.javadecklink.Capture.CodecID;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;

public class BlackmagicVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{

   /**
    * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
    */

   private final int decklink;
   private final YoVariableLoggerOptions options;
   private Capture capture;

   private final CircularLongMap circularLongMap = new CircularLongMap(10000);

   private FileWriter timestampWriter;

   private ArrayList<String> timestampDataArray = new ArrayList<>();

   private int frame;

   private volatile long lastFrameTimestamp = 0;

   public BlackmagicVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
   {
      super(logPath, logProperties, name);
      decklink = decklinkID;
      this.options = options;

      createCaptureInterface();
   }

   private void createCaptureInterface()
   {
      File timestampFile = new File(timestampData);

      switch (options.getVideoCodec())
      {
         case AV_CODEC_ID_H264:
            capture = new Capture(this, CodecID.AV_CODEC_ID_H264);
            capture.setOption("g", "1");
            capture.setOption("crf", String.valueOf(options.getCrf()));
            capture.setOption("profile", "high");
            capture.setOption("coder", "vlc");
            break;
         case AV_CODEC_ID_MJPEG:
            capture = new Capture(this, CodecID.AV_CODEC_ID_MJPEG);
            capture.setMJPEGQuality(options.getVideoQuality());
            break;
         default:
            throw new RuntimeException();
      }

      try
      {
         timestampWriter = new FileWriter(timestampFile);
         capture.startCapture(videoFile, decklink);
      }
      catch (IOException e)
      {
         capture = null;
         if (timestampWriter != null)
         {
            try
            {
               timestampWriter.close();
               timestampFile.delete();
            }
            catch (IOException e1)
            {
            }
         }
         timestampWriter = null;
         LogTools.info("Cannot start capture interface");
         e.printStackTrace();
      }
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#restart()
    */
   @Override
   public void restart() throws IOException
   {
      close();
      removeLogFiles();
      createCaptureInterface();
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#timestampChanged(long)
    */
   @Override
   public void timestampChanged(long newTimestamp)
   {
      if (capture != null)
      {
         long hardwareTimestamp = capture.getHardwareTime();
         if (hardwareTimestamp != -1)
         {
            circularLongMap.insert(hardwareTimestamp, newTimestamp);
         }
      }
   }

   /*
    * (non-Javadoc)
    * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#close()
    */
   @Override
   public void close()
   {
      LogTools.info("Signalling recorder to shut down.");
      if (capture != null)
      {
         try
         {
            writeTimestampData();

            LogTools.info("Stopping capture.");
            capture.stopCapture();
            LogTools.info("Closing writer.");
            timestampWriter.close();
            LogTools.info("Done.");
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         capture = null;
         timestampWriter = null;
      }

   }

   @Override
   public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
   {
      if (circularLongMap.size() > 0)
      {
         if (frame % 600 == 0)
         {
            double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
            System.out.println("[Decklink " + decklink + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
         }

         long robotTimestamp = circularLongMap.getValue(true, hardwareTime);

         if (frame == 0)
         {
            timestampDataArray.add(timeScaleNumerator + "\n");
            timestampDataArray.add(timeScaleDenumerator + "\n");

//               timestampWriter.write(timeScaleNumerator + "\n");
//               timestampWriter.write(timeScaleDenumerator + "\n");
         }

         timestampDataArray.add(robotTimestamp + " " + pts + "\n");
         //            timestampWriter.write(robotTimestamp + " " + pts + "\n");

         lastFrameTimestamp = System.nanoTime();
         ++frame;
      }

   }

   public void writeTimestampData()
   {
      try
      {
         for (String s : timestampDataArray)
         {
            timestampWriter.write(s);
         }
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   @Override
   public long getLastFrameReceivedTimestamp()
   {
      return lastFrameTimestamp;
   }

}
