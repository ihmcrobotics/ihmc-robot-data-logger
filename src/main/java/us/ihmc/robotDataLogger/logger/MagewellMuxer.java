package us.ihmc.robotDataLogger.logger;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegFrameRecorder.Exception;
import org.bytedeco.javacv.Frame;

import java.io.File;

public class MagewellMuxer
{
   private final FFmpegFrameRecorder recorder;

   public MagewellMuxer(File videoCaptureFile, int captureWidth, int captureHeight)
   {
      recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);

      // These settings have the best performance (H264 is a bad setting because of slicing)
      recorder.setVideoOption("tune", "zerolatency");
      recorder.setFormat("mov");
      // This video codec is deprecated, so in order to use it without errors we have to set the pixel format and strictly allow FFMPEG to use it
      recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
      recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
      recorder.setVideoOption("strict", "-2");
      // Frame rate of video recordings
      recorder.setFrameRate(60);
   }

   public void start() throws Exception
   {
      recorder.start();
   }

   /**
    * This method only works if {@link MagewellMuxer#start()} has been called first
    * @param capturedFrame the frame we want to save to the video
    * @param videoTimestamp is the timestamp in which to set the frame at
    * @return the timestamp of the frame we just recorded
    */
   public long recordFrame(Frame capturedFrame, long videoTimestamp)
   {
      // Ensure the video timestamp is ahead of the record's current timestamp
      if (videoTimestamp > recorder.getTimestamp())
      {
         // We tell the recorder to write this frame at this timestamp
         recorder.setTimestamp(videoTimestamp);
      }

      // This is where a frame is record, and we then need to store the timestamps, so they are synced
      try
      {
         recorder.record(capturedFrame);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }

      return recorder.getTimestamp();
   }

   public boolean isCloseOutputStream()
   {
      return recorder.isCloseOutputStream();
   }

   public long getTimeStamp()
   {
      return recorder.getTimestamp();
   }

   public void close()
   {
      recorder.setCloseOutputStream(true);
      try
      {
         recorder.flush();
         recorder.stop();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
}
