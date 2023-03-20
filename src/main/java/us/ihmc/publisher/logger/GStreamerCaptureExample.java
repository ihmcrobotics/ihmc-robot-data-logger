package us.ihmc.publisher.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.sun.jna.ptr.LongByReference;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.lowlevel.GstBufferAPI;
import org.freedesktop.gstreamer.lowlevel.GstClockAPI;
import org.freedesktop.gstreamer.lowlevel.GstVideoAPI;
import org.freedesktop.gstreamer.message.Message;
import org.freedesktop.gstreamer.message.MessageType;
import org.freedesktop.gstreamer.query.Query;
import us.ihmc.commons.thread.ThreadTools;

public class GStreamerCaptureExample
{
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static final ArrayList<Long> presentationTimestampData = new ArrayList<>(); // Often written as (pts)
    private static final ArrayList<Integer> indexData = new ArrayList<>();
    private static FileWriter timestampWriter;
    private static Element decklinkvideosrc;



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
                "decklinkvideosrc connection=sdi name=decklinkvideosrc " +
                        "! timeoverlay " +
                        "! videoconvert " +
                        "! videorate " +
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

        decklinkvideosrc = pipeline.getElementByName("decklinkvideosrc");

        // Allows a Pad.PROBE to be added to the pipeline for getting accurate measure of timestamps for each frame
        Element identity = pipeline.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new TimestampProbe());

        // Captures video for 10 seconds
        pipeline.play();

        // connect to the bus to receive messages
        Bus bus = pipeline.getBus();
        bus.connect(new Bus.MESSAGE()
                    {
                        @Override
                        public void busMessage(Bus bus, Message message)
                        {
                            if (message.getType() == MessageType.ELEMENT)
                            {
                                // get the video frame metadata
                                Element element = (Element) message.getSource();
                                Structure structure = message.getStructure();
                                System.out.println(structure.getValue("decklinkvideosrc"));
//                                GstBufferAPI meta = GstVideoFrameMetaAPI.GST_VIDEO_FRAME_META_GET(message, element);
//                                if (structure != null)
//                                {
//                                    ByteBuffer buffer = structure.g
//                                     get the hardware timestamp of the frame
//                                    long timestamp = meta.getHardwareTimestamp();
//                                    System.out.println("Hardware timestamp: " + timestamp);
//                                }
                            }
                        }
                    });

        ThreadTools.sleepSeconds(10);

        // Sends an event to shut down the pipeline correclty
        pipeline.sendEvent(new EOSEvent());
        gotEOSPlayBin.acquire(1);

        pipeline.stop();
        System.out.println("Stopped Capture");

        // Writes timestamp data to file
        writeTimestampFile();

    }

    private static void writeTimestampFile() throws IOException
    {
        timestampWriter.write("1\n");
        timestampWriter.write("50000\n");

        for (int i = 0; i < presentationTimestampData.size(); i++)
        {
            timestampWriter.write(presentationTimestampData.get(i) + " " + indexData.get(i) + "\n");
        }

        timestampWriter.close();
    }


    static class TimestampProbe implements Pad.PROBE
    {
        int i = 0;

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();

            // If buffer has a frame, record the timestamp and index from it
            if (buffer.isWritable())
            {

//                buffer.getDecodeTimestamp();
//                Meta meta = buffer.getMeta(GstVideoAPI.GSTVIDEO_API);


//                // Create a pointer to a long variable to hold the hardware time value
//                LongByReference hardwareTime = new LongByReference();
//
//                long testing = decklinkvideosrc.queryPosition(Format.TIME);
//
//                // Get the current hardware time
//                if (testing != -1) {
//                    System.out.println("Hardware time: " + hardwareTime.getValue());
//                } else {
//                    System.out.println("Failed to get hardware time");
//                }


                presentationTimestampData.add(buffer.getPresentationTimestamp());
                indexData.add(i);
                i += 100;
            }

            return PadProbeReturn.OK;
        }
    }
}