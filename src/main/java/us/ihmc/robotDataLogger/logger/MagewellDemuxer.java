package us.ihmc.robotDataLogger.logger;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import us.ihmc.log.LogTools;

import java.io.File;

/**
 * This class takes a video file and returns given information about its frames when requested
 */
public class MagewellDemuxer
{
    private static final String MAGEWELL_DEMUXER = "MageWell Demuxer";
    private final FFmpegFrameGrabber grabber;

    public MagewellDemuxer(File videoFile)
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
        return MAGEWELL_DEMUXER;
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
        seekToPTS(videoTimestamp, false);
    }

    /**
     * Seeks the underlying grabber to {@code videoTimestamp}.
     * <p>
     * When {@code fast} is {@code false}, this matches the historical behavior: the grabber decodes
     * forward from the nearest preceding keyframe up to the exact target PTS, so the next
     * {@link #getNextFrame()} call returns the frame at or just past the target.
     * </p>
     * <p>
     * When {@code fast} is {@code true}, the grabber only seeks to the nearest preceding keyframe
     * and does not decode forward to the target. The caller is then responsible for decoding
     * forward via {@link #getNextFrame()} until reaching the desired PTS. This trades exact
     * landing for substantially lower seek latency, which is useful when many seeks are issued in
     * rapid succession (e.g. while a user is dragging a scrubber slider) and the caller is willing
     * to display an intermediate frame.
     * </p>
     */
    public void seekToPTS(long videoTimestamp, boolean fast)
    {
        try
        {
            grabber.setTimestamp(videoTimestamp, fast);
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
            return grabber.grabFrame();
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
