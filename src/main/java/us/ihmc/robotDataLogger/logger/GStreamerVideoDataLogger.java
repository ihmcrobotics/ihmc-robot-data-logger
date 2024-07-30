package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.Conversions;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;

public class GStreamerVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{
    /**
     * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
     */

    private static boolean WRITTEN_TO_TIMESTAMP = false;
    private static long latestHardwareTimestamp;

    private FileWriter timestampWriter;
    private final Semaphore gotEOSPlayBin = new Semaphore(1);
    private final CircularLongMap circularLongMap = new CircularLongMap(10000);
    private Pipeline pipeline;

    private int frameNumber;
    private final int decklinkID;
    private static volatile long lastFrameTimestamp = 0;
    private static int i = 0;


    public GStreamerVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
    {
        super(logPath, null, logProperties, name);

        this.decklinkID = decklinkID;
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
        LogTools.info("Starting Gstreamer with camera index: " + decklinkID);
        Gst.init();

        String deckLinkIndex = " device-number=" + decklinkID + " ";

        pipeline = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=hdmi " + deckLinkIndex +
                "! timeoverlay " +
                "! videorate ! video/x-raw,framerate=60/1 " +
                "! identity name=identity " +
                "! jpegenc " +
                "! .video splitmuxsink muxer=qtmux location=" + videoCaptureFie);

        pipeline.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved the EOS on the pipeline!");
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
        if (pipeline != null)
        {
            if (latestHardwareTimestamp != System.currentTimeMillis())
            {
                circularLongMap.insert(System.currentTimeMillis(), newTimestamp);
                latestHardwareTimestamp = System.currentTimeMillis();
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
        try
        {
            LogTools.info("Stopping capture.");
            pipeline.sendEvent(new EOSEvent());
            gotEOSPlayBin.acquire(1);
            pipeline.stop();

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

    @Override
    public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
    {

        if (circularLongMap.size() > 0)
        {
            long robotTimestamp = circularLongMap.getValue(true, hardwareTime);

            if (frameNumber % 420 == 0)
            {
                double delayInSeconds = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
                System.out.println("Delay in Seconds: " + delayInSeconds + " / PresentationTimeStamp: " + pts);
            }

            try
            {
                if (!WRITTEN_TO_TIMESTAMP)
                {
                    timestampWriter.write(timeScaleNumerator + "\n");
                    timestampWriter.write(timeScaleDenumerator + "\n");
                    WRITTEN_TO_TIMESTAMP = true;
                }

                timestampWriter.write(robotTimestamp + " " + pts + "\n");

                lastFrameTimestamp = System.nanoTime();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            ++frameNumber;
        }
    }

    @Override
    public long getLastFrameReceivedTimestamp()
    {
        return lastFrameTimestamp;
    }


    class TimestampProbe implements Pad.PROBE
    {
        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            if (buffer.isWritable())
            {
                //One will work with SCS2Visualizer, the other works with VideoDataPlayer
//                receivedFrameAtTime(System.currentTimeMillis(), buffer.getPresentationTimestamp(), 1, 60000);
                receivedFrameAtTime(System.currentTimeMillis(), i, 1, 90000);
                // Val setup Timestamps
                i += 3001;
                // Nothing setup Timestamps
                i += 1001;
            }

            return PadProbeReturn.OK;
        }
    }
}
