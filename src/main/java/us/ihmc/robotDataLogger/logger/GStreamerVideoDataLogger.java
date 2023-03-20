package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    private static long lastestRobotTimestamp;

    private final Semaphore gotEOSPlayBin = new Semaphore(1);
    private final int decklinkID;

    private Pipeline pipeline;

    private static volatile long lastFrameTimestamp = 0;

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
                "decklinkvideosrc connection=sdi " + deckLinkIndex +
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
            System.out.println("Saving newRobotTimeStamp");
            lastestRobotTimestamp = newTimestamp;
//            nanoToHardware.insert(hardwareTimestamp, newTimestamp);
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
//        if (frame % 600 == 0)
//        {
//            double delayInS = Conversions.nanosecondsToSeconds(nanoToHardware.getLatestKey() - hardwareTime);
//            System.out.println("[Decklink] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
//        }

//            long robotTimestamp = nanoToHardware.getValue(true, hardwareTime);
        long robotTimestamp = hardwareTime;

        try
        {
            if (!WRITTEN_TO_TIMESTAMP)
            {
                timestampWriter.write(timeScaleNumerator + "\n");
                timestampWriter.write(timeScaleDenumerator + "\n");
                WRITTEN_TO_TIMESTAMP = true;
            }

//            System.out.println("Writing Stuffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

            timestampWriter.write(robotTimestamp + " " + pts + "\n");

            lastFrameTimestamp = System.nanoTime();
        }
        catch (IOException e)
        {
            e.printStackTrace();
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
//                System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^6()()()");
                receivedFrameAtTime(lastestRobotTimestamp, buffer.getPresentationTimestamp(), 1, 60000);
//                presentationTimestampData.add(buffer.getPresentationTimestamp());
            }

            return PadProbeReturn.OK;
        }
    }
}
