package us.ihmc.publisher.logger;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javafx.scene.paint.Paint;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
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

//        Bin videoBin = Gst.parseBinFromDescription(
//                "appsink name=videoAppSink",
//                true);

        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! videoconvert " +
                "! videorate " +
                "! identity name=identity " +
                "! x264enc " +
                "! mp4mux " +
                "! filesink location=/home/nick/xyz.mov");
//                "! filesink location=/home/nick/xyz.mp4");

        pipe.getBus().connect((Bus.EOS) (source) ->
        {
            System.out.println("Recieved the EOS on the pipeline!!!");
            gotEOSPlayBin.release();
        });

        gotEOSPlayBin.drainPermits();

        Element identity = pipe.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new Renderer(WIDTH, HEIGHT));

        pipe.play();
        ThreadTools.sleepSeconds(10);

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

    static class Renderer implements Pad.PROBE {

        private final BufferedImage image;
        private final int[] data;
        private final Point[] points;
//        private final Paint fill;

        private Renderer(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            data = ((DataBufferInt) (image.getRaster().getDataBuffer())).getData();
            points = new Point[18];
            for (int i = 0; i < points.length; i++) {
                points[i] = new Point();
            }
//            fill = new GradientPaint(0, 0, new Color(1.0f, 0.3f, 0.5f, 0.9f),
//                    60, 20, new Color(0.3f, 1.0f, 0.7f, 0.8f), true);
        }

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info) {
            Buffer buffer = info.getBuffer();
            if (buffer.isWritable()) {
//                IntBuffer ib = buffer.map(true).asIntBuffer();
//                ib.get(data);
//                render();
//                ib.rewind();
//                ib.put(data);
//                buffer.unmap();
                System.out.println("Running + " + System.nanoTime());
            }
            return PadProbeReturn.OK;
        }
    }
}