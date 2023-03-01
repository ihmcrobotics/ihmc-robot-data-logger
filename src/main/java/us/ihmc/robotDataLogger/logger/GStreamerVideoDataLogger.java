package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;

public class GStreamerVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{
    /**
     * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
     */
    private static boolean WRITTEN_TO_TIMESTAMP = false;

    private final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> presentationTimestampData = new ArrayList<>();
    private static final ArrayList<Integer> indexData = new ArrayList<>();

    private Pipeline pipeline;

    private static FileWriter timestampWriter;

    public GStreamerVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
    {
        super(logPath, logProperties, name);

        createCaptureInterface();
    }

    private void createCaptureInterface() throws IOException
    {
        File timestampFile = new File(timestampData);
        File videoCaptureFile = new File(videoFile);

        timestampWriter = new FileWriter(timestampFile);

        startCapture(videoCaptureFile);
    }

    public void startCapture(File videoCaptureFie)
    {
        Gst.init();

        pipeline = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! timeoverlay " +
                "! videoconvert " +
                "! videorate " +
                "! identity name=identity " +
                "! jpegenc " +
                "! .video splitmuxsink muxer=qtmux location=" + videoCaptureFie);

        pipeline.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved the EOS on the pipeline!!!");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        Element identity = pipeline.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new TimestampProbe());

        pipeline.play();
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
    }

    /*
     * (non-Javadoc)
     * @see us.ihmc.robotDataLogger.logger.VideoDataLoggerInterface#close()
     */
    @Override
    public void close()
    {
        LogTools.info("Signalling recorder to shut down.");
        try
        {
            LogTools.info("Stopping capture.");
            pipeline.sendEvent(new EOSEvent());
            gotEOSPlayBin.acquire(1);
            pipeline.stop();

            if (!WRITTEN_TO_TIMESTAMP)
            {
                LogTools.info("Writing Timestamp");
                writingToTimestamp();
            }

            LogTools.info("Closing writer.");
            timestampWriter.close();

            LogTools.info("Done.");
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }

        timestampWriter = null;
    }

    public static void writingToTimestamp() throws IOException
    {
        timestampWriter.write("1\n");
        timestampWriter.write("60000\n");

        for (int i = 0; i < presentationTimestampData.size(); i++)
        {
            timestampWriter.write(presentationTimestampData.get(i) + " " + indexData.get(i) + "\n");
        }

        timestampWriter.close();

        WRITTEN_TO_TIMESTAMP = true;
    }

    @Override
    public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
    {
    }

    @Override
    public long getLastFrameReceivedTimestamp()
    {
        return 0;
    }


    static class TimestampProbe implements Pad.PROBE
    {
        int i = 1001;

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            if (buffer.isWritable())
            {
                presentationTimestampData.add(buffer.getPresentationTimestamp());
                indexData.add(i);
                i += 1001;
            }

            return PadProbeReturn.OK;
        }
    }
}
