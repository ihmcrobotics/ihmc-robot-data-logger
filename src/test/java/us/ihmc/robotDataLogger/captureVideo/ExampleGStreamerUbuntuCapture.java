package us.ihmc.robotDataLogger.captureVideo;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * This example class provides the basics for capturing a video using GStreamer java bindings
 * You need a decklink capture card, and a camera attached on the other end for this to work.
 *The camera settings get overridden, so it doesn't matter what video mode or FPS its set too.
 * This only works on Ubuntu Software
 * This example works with either SDI or HDMI as the camera cable
 */
public class ExampleGStreamerUbuntuCapture
{
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);

    private static final String ubuntuGStreamer = "ubuntuGStreamer";

    public static String videoPath;
    public static String timestampPath;
    private static FileWriter timestampWriter;

    public static File videoFile;
    public static File timestampFile;

    public static void main(String[] args) throws InterruptedException, IOException
    {
        videoPath  =  "ihmc-robot-data-logger/out/" + ubuntuGStreamer + "_Video.mov";
        timestampPath = "ihmc-robot-data-logger/out/" + ubuntuGStreamer + "_Timestamps.dat";

        videoFile = new File(videoPath);
        timestampFile = new File(timestampPath);

        // Creates files for video
        setupTimestampWriter();

        // Ensures GStreamer is set up correctly
        Gst.init();

        // How the data will be parsed through the pipeline when its playing
        Pipeline pipeline = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                        "! timeoverlay " +
                        "! videoconvert " +
                            // Set the framerate here
                        "! videorate ! video/x-raw,framerate=60/1 " +
                        "! identity name=identity " +
                        "! jpegenc " +
                        "! .video splitmuxsink muxer=qtmux location=" + videoFile);

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

    public static void setupTimestampWriter()
    {
        try
        {
            timestampWriter = new FileWriter(timestampFile);
            timestampWriter.write(1 + "\n");
            timestampWriter.write(60000 + "\n");
        }
        catch (IOException e)
        {
            LogTools.info("Didn't setup the timestamp file correctly");
        }
    }

    /**
     * Write the timestamp sent from the controller, and then we get the timestamp of the camera, and write both
     * of those to a file.
     */
    public static void writeTimestampToFile(long controllerTimestamp, long pts)
    {
        try
        {
            timestampWriter.write(controllerTimestamp + " " + pts + "\n");
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
                writeTimestampToFile(System.nanoTime(), buffer.getPresentationTimestamp());
            }

            return PadProbeReturn.OK;
        }
    }
}