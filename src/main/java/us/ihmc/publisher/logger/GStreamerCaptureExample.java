package us.ihmc.publisher.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.thread.ThreadTools;

public class GStreamerCaptureExample
{
    private static boolean WRITTEN_TO_TIMESTAMP = false;
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static FileWriter timestampWriter;

    private static volatile long lastFrameTimestamp = 0;
    private static long lastestRobotTimestamp;


    public static void main(String[] args) throws InterruptedException, IOException
    {
        // Creates files for video and timestamps
        File timestampFile = new File(System.getProperty("user.home") + File.separator + "gstreamerTimestamps.dat");
        File videoCaptureFile = new File(System.getProperty("user.home") + File.separator + "gstreamerCapture.mov");
        timestampFile.createNewFile();
        timestampWriter = new FileWriter(timestampFile);

        // Ensures GStreamer is set up correctly
        Gst.init();

        // How the data will be parsed through the pipeline when its playing
        Pipeline pipeline = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=hdmi " +
                        "! timeoverlay " +
                        "! videoconvert " +
                            // Set the framerate here
                        "! videorate ! video/x-raw,framerate=60/1 " +
                        "! identity name=identity " +
                        "! jpegenc " +
                        "! .video splitmuxsink muxer=qtmux location=" + videoCaptureFile);

        // Allows for correctly shutting down when it detects the pipeline has ended
        pipeline.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved EOS on pipeline");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        // Allows a Pad.PROBE to be added to the pipeline for getting accurate measure of timestamps for each frame
        Element identity = pipeline.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new TimestampProbe());

        // Captures video for 10 seconds
        pipeline.play();
        ThreadTools.sleepSeconds(10);

        // Sends an event to shut down the pipeline correclty
        pipeline.sendEvent(new EOSEvent());
        gotEOSPlayBin.acquire(1);

        pipeline.stop();
        System.out.println("Stopped Capture");

        // Writes timestamp data to file
        timestampWriter.close();

    }

    //This is used for the logger robottimestamps, here for testing
    public void timestampChanged(long newTimestamp)
    {
        System.out.println("Saving newRobotTimeStamp");
        lastestRobotTimestamp = newTimestamp;
//            nanoToHardware.insert(hardwareTimestamp, newTimestamp);
    }

    public static void receivedFrameAtTime(long hardwareTime, long pts, long timeScaleNumerator, long timeScaleDenumerator)
    {
        long robotTimestamp = hardwareTime;

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
    }

    static class TimestampProbe implements Pad.PROBE
    {
        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            // If buffer has a frame, record the timestamp and index from it
            if (buffer.isWritable())
            {
                receivedFrameAtTime(lastestRobotTimestamp, buffer.getPresentationTimestamp(), 1, 60000);
            }

            return PadProbeReturn.OK;
        }
    }
}