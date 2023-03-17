package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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

    private final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> presentationTimestampData = new ArrayList<>();
    private static final ArrayList<Integer> indexData = new ArrayList<>();
    private final int decklinkID;

    private static final CircularLongMap frameToNano = new CircularLongMap(10000);
    private final CircularLongMap nanoToHardware = new CircularLongMap(10000);

    private Pipeline pipeline;

    private int frame;
    private volatile long lastFrameTimestamp = 0;

    private static FileWriter timestampWriter;

    public GStreamerVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
    {
        super(logPath, logProperties, name);

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
//                "decklinkvideosrc connection=sdi device-number=1 " +
                "decklinkvideosrc connection=hdmi " + deckLinkIndex +
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
        if (pipeline != null)
        {// Progably a good idea to print the Blackmagic keys and such to see what we are working with
            long hardwareTimestamp = getNanoTime();
            if (hardwareTimestamp != -1)
            {
                nanoToHardware.insert(hardwareTimestamp, newTimestamp);
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

//            if (!WRITTEN_TO_TIMESTAMP)
//            {
//                LogTools.info("Writing Timestamp");
//                writingToTimestamp();
//            }

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
        System.out.println("*");
        if (nanoToHardware.size() > 0)
        {
            System.out.println("------------------");
            if (frame % 600 == 0)
            {
                double delayInS = Conversions.nanosecondsToSeconds(nanoToHardware.getLatestKey() - hardwareTime);
                System.out.println("[Decklink] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
            }

            long robotTimestamp = nanoToHardware.getValue(true, hardwareTime);

            try
            {
                if (frame == 0)
                {
                    timestampWriter.write(timeScaleNumerator + "\n");
                    timestampWriter.write(timeScaleDenumerator + "\n");
                }
                timestampWriter.write(robotTimestamp + " " + pts + "\n");

                lastFrameTimestamp = System.nanoTime();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            ++frame;
        }

    }

    @Override
    public long getLastFrameReceivedTimestamp()
    {
        return lastFrameTimestamp;
    }


    static class TimestampProbe implements Pad.PROBE
    {
        int i = 0;

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            if (buffer.isWritable())
            {
                frameToNano.insert(System.nanoTime(), buffer.getPresentationTimestamp());
                presentationTimestampData.add(buffer.getPresentationTimestamp());
                indexData.add(i);
                i += 100;
            }

            return PadProbeReturn.OK;
        }
    }

    private long getNanoTime()
    {
        return frameToNano.getValue(true, System.nanoTime());
    }
}
