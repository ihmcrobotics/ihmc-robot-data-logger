package us.ihmc.publisher.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.thread.ThreadTools;

public class GStreamerCaptureExample
{
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> presentationTimestampData = new ArrayList<>(); // Often written as (pts)
    private static final ArrayList<Integer> indexData = new ArrayList<>();
    private static FileWriter timestampWriter;



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
        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! timeoverlay " +
                "! videoconvert " +
                "! videorate " +
                "! identity name=identity " +
                "! jpegenc " +
                "! .video splitmuxsink muxer=qtmux location=" + videoCaptureFile);

        // Allows for correctly shutting down when it detects the pipeline has ended
        pipe.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved EOS on pipeline");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        // Allows a Pad.PROBE to be added to the pipeline for getting accurate measure of timestamps for each frame
        Element identity = pipe.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new TimestampProbe());

        // Captures video for 10 seconds
        pipe.play();
        ThreadTools.sleepSeconds(10);

        // Sends an event to shut down the pipeline correclty
        pipe.sendEvent(new EOSEvent());
        gotEOSPlayBin.acquire(1);

        pipe.stop();
        System.out.println("Stopped Capture");

        // Writes timestamp data to file
        writeTimestampFile();

    }

    private static void writeTimestampFile() throws IOException
    {
        timestampWriter.write("1\n");
        timestampWriter.write("60000\n");

        for (int i = 0; i < presentationTimestampData.size(); i++)
        {
            timestampWriter.write(presentationTimestampData.get(i) + " " + indexData.get(i) + "\n");
        }

        timestampWriter.close();
    }


    static class TimestampProbe implements Pad.PROBE
    {
        int i = 1001;

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            // If buffer has a frame, record the timestamp and index from it
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