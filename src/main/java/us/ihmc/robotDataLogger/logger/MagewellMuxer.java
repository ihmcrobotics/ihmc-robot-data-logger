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
   private boolean closed = false;

   public MagewellMuxer(File videoCaptureFile, int captureWidth, int captureHeight)
   {
      recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);

      recorder.setVideoOption("tune", "zerolatency"); // https://trac.ffmpeg.org/wiki/StreamingGuide
      recorder.setFormat("mov");

      // For information about these settings visit https://trac.ffmpeg.org/wiki/Encode/H.264
      recorder.setVideoOption("preset", "ultrafast");
      recorder.setVideoOption("crf", "27");
      // g=1 forces every frame to be a keyframe (all-intra), so random-access seeking in the demuxer works correctly
      recorder.setVideoOption("g", "1");
      recorder.setVideoBitrate(60000000); // 6000 kb/s

      recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
      recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
      // Frame rate of video recordings
      recorder.setFrameRate(60);
   }

   public void start() throws Exception
   {
      recorder.start();
   }

   /**
    * This method only works if {@link MagewellMuxer#start()} has been called first
    *
    * @param capturedFrame  the frame we want to save to the video
    * @param videoTimestamp is the timestamp in which to set the frame at
    */
   public void recordFrame(Frame capturedFrame, long videoTimestamp)
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
         if (!closed)
         {
            recorder.record(capturedFrame);
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public boolean isClosed()
   {
      return closed;
   }

   public long getTimeStamp()
   {
      return recorder.getTimestamp();
   }

   public void close()
   {
      closed = true;
      try
      {
         recorder.stop();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
}
