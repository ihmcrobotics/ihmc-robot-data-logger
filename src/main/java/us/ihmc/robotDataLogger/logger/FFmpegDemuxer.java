package us.ihmc.robotDataLogger.logger;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import us.ihmc.log.LogTools;

import java.io.File;

/**
 * Generic FFmpeg-backed demuxer: takes a video file and returns given information about its frames
 * when requested. Not specific to any particular capture card - used for both Magewell and
 * BlackMagic recordings, which both end up as plain MP4/MOV files.
 */
public class FFmpegDemuxer
{
    private static final String FFMPEG_DEMUXER = "FFmpeg Demuxer";
    private final FFmpegFrameGrabber grabber;

    public FFmpegDemuxer(File videoFile)
    {
        try
        {
            grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();
        }
        catch (FrameGrabber.Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getName()
    {
        return FFMPEG_DEMUXER;
    }

    public int getImageHeight()
    {
        return grabber.getImageHeight();
    }

    public int getImageWidth()
    {
        return grabber.getImageWidth();
    }

    public long getCurrentPTS()
    {
        return grabber.getTimestamp();
    }

    public void seekToPTS(long videoTimestamp)
    {
        try
        {
            // Frame-accurate seek: seeks to the preceding keyframe, then decodes forward to the requested
            // timestamp. Required because the muxer no longer encodes every frame as a keyframe.
            grabber.setVideoTimestamp(videoTimestamp);
        }
        catch (FFmpegFrameGrabber.Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public int getFrameNumber()
    {
        return grabber.getFrameNumber();
    }

    public Frame getNextFrame()
    {
        try
        {
            // grabImage() (not the generic grabFrame()) so the grabber itself skips over interleaved
            // audio/timecode packets at the native av_read_frame level - with no arbitrary retry cap -
            // instead of ever handing callers a non-video packet to skip themselves.
            return grabber.grabImage();
        }
        catch (FrameGrabber.Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public double getFrameRate()
    {
        return grabber.getVideoFrameRate();
    }

    public void stop()
    {
       try
       {
           grabber.stop();
       }
       catch (FFmpegFrameGrabber.Exception e)
       {
           LogTools.error(e.getMessage());
       }
    }
}
