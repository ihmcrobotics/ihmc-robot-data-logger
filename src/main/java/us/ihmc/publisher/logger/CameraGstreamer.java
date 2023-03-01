package us.ihmc.publisher.logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.video.VideoTimeCode;
import org.freedesktop.gstreamer.video.VideoTimeCodeMeta;
import us.ihmc.commons.thread.ThreadTools;

public class CameraGstreamer
{
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private final static int BUFFER_SIZE = 100;

    private static boolean sendData = false;
    private static ArrayBlockingQueue<Buffer> videoQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
    private static ArrayBlockingQueue<Buffer> audioQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
    private static StringBuffer videoCaps = new StringBuffer();
    private static StringBuffer audioCaps = new StringBuffer();
    private static Semaphore gotCaps = new Semaphore(2);
    private static final Semaphore gotEOSPlayBin = new Semaphore(1);
    private static Semaphore canSend = new Semaphore(2);

    public static void main(String[] args) throws InterruptedException
    {
        Gst.init();

        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! timeoverlay " +
                "! videoconvert " +
                "! videorate " +
                "! identity name=identity " +
                "! jpegenc " +
                "! .video splitmuxsink muxer=qtmux location=/home/nick/perfectSAVE.mov");// +
//                "! filesink location=/home/nick/xyz.mov");
//                "! filesink location=/home/nick/xyz.mp4");

        pipe.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved the EOS on the pipeline!!!");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        Element identity = pipe.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new Renderer());

        pipe.play();
        ThreadTools.sleepSeconds(120);

        pipe.sendEvent(new EOSEvent());
        gotEOSPlayBin.acquire(1);

        System.out.println("Stopped Capture");

        pipe.stop();

//        Gst.getExecutor().schedule(Gst::quit, 10, TimeUnit.SECONDS);
//        Gst.main();

//        pipe.sendEvent(new EOSEvent());
//
//        pipe.stop();
//        ThreadTools.sleepSeconds(4);

        // Doesn't shut down the pipeline properly so the video is lost.
    }

    static class Renderer implements Pad.PROBE
    {

        int i = 1001;

        private Renderer()
        {
            System.out.println("1");
            System.out.println("60000");
        }

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
        {
            Buffer buffer = info.getBuffer();
//            VideoTimeCodeMeta meta = buffer.getMeta(VideoTimeCodeMeta.API);
//            assertNotNull(meta);
//            VideoTimeCode timeCode = meta.getTimeCode();
//            System.out.println(timeCode.getFrames());

            if (buffer.isWritable())
            {
                System.out.println(buffer.getPresentationTimestamp() + " " + i);
//                System.out.println(System.nanoTime() + " " + i);
                i += 1001;
            }
            return PadProbeReturn.OK;
        }
    }
}