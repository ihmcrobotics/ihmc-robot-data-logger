package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javadecklink.CaptureHandler;
import us.ihmc.log.LogTools;
import us.ihmc.publisher.logger.ExampleGStreamerCapture;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.tools.maps.CircularLongMap;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

public class GStreamerVideoDataLogger extends VideoDataLoggerInterface implements CaptureHandler
{

    /**
     * Make sure to set a progressive mode, otherwise the timestamps will be all wrong!
     */
    private static boolean WRITTEN_TO_TIMESTAMP = false;

    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> ptsData = new ArrayList<>();
    private static final ArrayList<Integer> indexData = new ArrayList<>();
    Pipeline pipeline;

//    String timestampData = System.getProperty("user.home") + File.separator + "gstreamerTimestamps.dat";
//    String videoFile = System.getProperty("user.home") + File.separator + "gstreamerCapture.mov";

    private final int decklink;
    private final YoVariableLoggerOptions options;


    private FFmpegFrameRecorder recorder;

    private final CircularLongMap circularLongMap = new CircularLongMap(10000);

    private static FileWriter timestampWriter;

    private int frame;

    private volatile long lastFrameTimestamp = 0;

    public GStreamerVideoDataLogger(String name, File logPath, LogProperties logProperties, int decklinkID, YoVariableLoggerOptions options) throws IOException
    {
        super(logPath, logProperties, name);
        decklink = decklinkID;
        this.options = options;

        createCaptureInterface();
    }

    private void createCaptureInterface() throws IOException
    {
        File timestampFile = new File(timestampData);
        File videoCaptureFile = new File(videoFile);

//        switch (options.getVideoCodec())
//        {
//            case AV_CODEC_ID_H264:
//            case AV_CODEC_ID_MJPEG:
//                recorder = new FFmpegFrameRecorder(videoCaptureFile, captureWidth, captureHeight);
//                recorder.setVideoOption("crf", "1");
//                recorder.setVideoOption("tune", "zerolatency");
//                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
//                recorder.setFormat("mov");
//                recorder.setFrameRate(120);
//                break;
//            default:
//                throw new RuntimeException();
//        }

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
        if (recorder != null)
        {
            long hardwareTimestamp = getHardwareTimestamp();

            if (hardwareTimestamp > 0)
            {
                LogTools.info("hardwareTimestamp={}, newTimestamp={}", hardwareTimestamp, newTimestamp);
                circularLongMap.insert(hardwareTimestamp, newTimestamp);
            }
        }
    }

    private long getHardwareTimestamp()
    {
        return Math.round(recorder.getFrameNumber() * 1000000000L / recorder.getFrameRate());
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

            LogTools.info("Writing Timestamp");
            if (!WRITTEN_TO_TIMESTAMP)
            {
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
        recorder = null;
        timestampWriter = null;
    }

    public static void writingToTimestamp() throws IOException
    {
        timestampWriter.write("1\n");
        timestampWriter.write("60000\n");

        for (int i = 0; i < ptsData.size(); i++)
        {
            timestampWriter.write(ptsData.get(i) + " " + indexData.get(i) + "\n");
        }

        timestampWriter.close();

        WRITTEN_TO_TIMESTAMP = true;
    }

    @Override
    public void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
    {
        if (circularLongMap.size() > 0)
        {
            if (frame % 600 == 0)
            {
                double delayInS = Conversions.nanosecondsToSeconds(circularLongMap.getLatestKey() - hardwareTime);
                System.out.println("[Decklink " + decklink + "] Received frame " + frame + ". Delay: " + delayInS + "s. pts: " + pts);
            }

            long robotTimestamp = circularLongMap.getValue(true, hardwareTime);

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
        int i = 1001;

        private TimestampProbe()
        {
            System.out.println("1");
            System.out.println("60000");
        }

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            if (buffer.isWritable())
            {
                ptsData.add(buffer.getPresentationTimestamp());
                indexData.add(i);
                System.out.println(buffer.getPresentationTimestamp() + " " + i);
                i += 1001;
            }

            return PadProbeReturn.OK;
        }
    }
}
