package us.ihmc.robotDataLogger.logger;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.JCommander;

import us.ihmc.javadecklink.Capture.CodecID;

import java.util.Objects;

public class YoVariableLoggerOptions
{
   public static final String defaultLogDirectory = System.getProperty("user.home") + "/robotLogs";
   public static final CodecID defaultCodec = CodecID.AV_CODEC_ID_H264;
   public static final double defaultVideoQuality = 0.85;
   public static final int defaultCRF = 23;
   private CodecID videoCodecID;

   @Parameter(names = {"-d", "--directory"}, description = "Directory where to save log files")
   private String logDirectory = defaultLogDirectory;

   @Parameter(names = {"-q", "--quality"}, description = "Video quality for MJPEG")
   private double videoQuality = defaultVideoQuality;

   @Parameter(names = {"-c", "--codec"}, description = "Desired video codec. AV_CODEC_ID_H264 or AV_CODEC_ID_MJPEG")
   private String videoCodec = defaultCodec.name(); // stored as string for parsing later

   @Parameter(names = {"-r", "--crf"}, description = "CRF (Constant rate factor) for H264. 0-51, 0 is lossless. Sane values are 18-28")
   private int crf = defaultCRF;

   @Parameter(names = {"-n", "--noVideo"}, description = "Disable video recording")
   private boolean disableVideo = false;

   @Parameter(names = {"-z", "--noZEDLogging"}, description = "Disable ZED Logging")
   private boolean disableZEDLogging = false;

   @Parameter(names = {"-s", "--sync"}, description = "Aggressively flush data to disk. Reduces chance of data loss")
   private boolean flushAggressivelyToDisk = false;

   @Parameter(names = {"-a", "--noDiscovery"}, description = "Disable autodiscovery of clients")
   private boolean disableAutoDiscovery = false;

   @Parameter(names = {"-o", "--rotate"}, description = "Rotate logs in incoming folder, keep n logs. Set to zero to keep all logs")
   private int rotateLogsCount = 0; // internally we'll compute rotateLogs and numberOfLogsToKeep

   @Parameter(names = {"-m", "--allowManyInstances"}, description = "Allow more than one instance of the logger at once")
   private boolean allowManyInstances = false;

   // Derived fields
   private boolean rotateLogs = false;
   private int numberOfLogsToKeep = Integer.MAX_VALUE;

   public static YoVariableLoggerOptions parse(String[] args)
   {
      YoVariableLoggerOptions options = new YoVariableLoggerOptions();
      JCommander jc = JCommander.newBuilder().addObject(options).build();

      try
      {
         jc.parse(args);
      }
      catch (ParameterException e)
      {
         System.err.println(e.getMessage());
         jc.usage();
         System.exit(-1);
      }

      options.setRotateLogs(options.rotateLogsCount);
      options.setVideoCodec(CodecID.valueOf(options.videoCodec));

      return options;
   }

   public void setRotateLogs(int logsToKeep)
   {
      if (logsToKeep > 0)
      {
         rotateLogs = true;
         numberOfLogsToKeep = logsToKeep;
      }
      else
      {
         rotateLogs = false;
         numberOfLogsToKeep = Integer.MAX_VALUE;
      }
   }

   public String getLogDirectory()
   {
      return logDirectory;
   }

   public double getVideoQuality()
   {
      return videoQuality;
   }

   public boolean getDisableVideo()
   {
      return disableVideo;
   }

   public boolean getDisableZEDLogging()
   {
      return disableZEDLogging;
   }

   public boolean isFlushAggressivelyToDisk()
   {
      return flushAggressivelyToDisk;
   }

   public boolean isDisableAutoDiscovery()
   {
      return disableAutoDiscovery;
   }

   public boolean isRotateLogs()
   {
      return rotateLogs;
   }

   public int getNumberOfLogsToKeep()
   {
      return numberOfLogsToKeep;
   }

   public boolean isAllowManyInstances()
   {
      return allowManyInstances;
   }

   public int getCrf()
   {
      return crf;
   }

   public CodecID getVideoCodec()
   {
      return Objects.requireNonNull(videoCodecID);
   }

   public void setVideoCodec(CodecID codec)
   {
      this.videoCodecID = codec;
   }
}
