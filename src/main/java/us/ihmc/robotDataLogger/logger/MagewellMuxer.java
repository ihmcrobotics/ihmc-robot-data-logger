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
   private volatile boolean closed = false;

   public MagewellMuxer(File videoCaptureFile, int captureWidth, int captureHeight)
   {
      recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);

      recorder.setVideoOption("tune", "zerolatency"); // https://trac.ffmpeg.org/wiki/StreamingGuide
      recorder.setFormat("mov");

      // For information about these settings visit https://trac.ffmpeg.org/wiki/Encode/H.264
      recorder.setVideoOption("preset", "ultrafast");
      recorder.setVideoOption("crf", "27");
      // GOP size: keyframe every ~1 second at 60 fps. The demuxer must use frame-accurate seeking
      // (FFmpegFrameGrabber.setVideoTimestamp) to land on a non-keyframe.
      recorder.setVideoOption("g", "60");
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

   /**
    * Signals that recording should stop. Safe to call from any thread - only sets a flag that the
    * capture loop in {@link MagewellVideoDataLogger#startCapture()} checks. Does not touch the
    * native recorder itself; see {@link #stopRecording()}.
    */
   public void close()
   {
      closed = true;
   }

   /**
    * Actually stops and releases the native recorder. {@code recorder}'s methods are not all
    * consistently synchronized against each other (e.g. {@code getTimestamp()}/{@code
    * setTimestamp()} aren't, even though {@code record()} is), so this must only ever be called
    * from the same thread that calls {@link #start()} and {@link #recordFrame} - i.e. the capture
    * thread, once its loop has exited - never concurrently from another thread such as one calling
    * {@link #close()}.
    */
   public void stopRecording()
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
