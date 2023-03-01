package us.ihmc.publisher.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.thread.ThreadTools;

public class ExampleGStreamerCapture
{
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> ptsData = new ArrayList<>();
    private static final ArrayList<Integer> indexData = new ArrayList<>();


    public static void main(String[] args) throws InterruptedException, IOException
    {
        File timestampFile = new File(System.getProperty("user.home") + File.separator + "gstreamerTimestamps.dat");
        timestampFile.createNewFile();

        FileWriter timestampWriter = new FileWriter(timestampFile);

        Gst.init();

        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! timeoverlay " +
                "! videoconvert " +
                "! videorate " +
                "! identity name=identity " +
                "! jpegenc " +
                "! .video splitmuxsink muxer=qtmux location=/home/nick/gstreamerCapture.mov");// +

        pipe.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved the EOS on the pipeline!!!");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        Element identity = pipe.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new TimestampProbe());

        pipe.play();
        ThreadTools.sleepSeconds(10);

        pipe.sendEvent(new EOSEvent());
        gotEOSPlayBin.acquire(1);

        System.out.println("Stopped Capture");

        pipe.stop();
        ThreadTools.sleepSeconds(1);

        timestampWriter.write("1\n");
        timestampWriter.write("60000\n");

        for (int i = 0; i < ptsData.size(); i++)
        {
            timestampWriter.write(ptsData.get(i) + " " + indexData.get(i) + "\n");
        }

        timestampWriter.close();
    }

    static class TimestampProbe implements Pad.PROBE
    {
        int i = 1001;

        private TimestampProbe()
        {
//            System.out.println("1");
//            System.out.println("60000");
        }

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            if (buffer.isWritable())
            {
                ptsData.add(buffer.getPresentationTimestamp());
                indexData.add(i);
//                System.out.println(buffer.getPresentationTimestamp() + " " + i);
                i += 1001;
            }

            return PadProbeReturn.OK;
        }
    }
}