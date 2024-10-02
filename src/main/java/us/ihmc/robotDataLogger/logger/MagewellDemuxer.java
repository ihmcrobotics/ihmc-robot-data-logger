package us.ihmc.robotDataLogger.logger;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

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
        try
        {
            grabber.setTimestamp(videoTimestamp);
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

    public int getBitRate()
    {
        return grabber.getVideoBitrate();
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

    public int getFrameRate()
    {
        return (int) grabber.getVideoFrameRate();
    }
}
