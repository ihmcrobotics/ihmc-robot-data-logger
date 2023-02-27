package us.ihmc.publisher.logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.EOSEvent;
import us.ihmc.commons.thread.ThreadTools;

public class CameraGstreamer
{

    private final static int BUFFER_SIZE = 100;

    private static boolean sendData = false;
    private static ArrayBlockingQueue<Buffer> videoQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
    private static ArrayBlockingQueue<Buffer> audioQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
    private static StringBuffer videoCaps = new StringBuffer();
    private static StringBuffer audioCaps = new StringBuffer();
    private static Semaphore gotCaps = new Semaphore(2);
    private static Semaphore canSend = new Semaphore(2);

    public static void main(String[] args)
    {
        Gst.init();

//        Bin videoBin = Gst.parseBinFromDescription(
//                "appsink name=videoAppSink",
//                true);

        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc connection=sdi " +
                "! videoconvert " +
                "! videorate " +
                "! x264enc " +
                "! mp4mux " +
                "! filesink location=/home/nick/xyz.mp4");

        pipe.play();
        ThreadTools.sleepSeconds(10);
        pipe.sendEvent(new EOSEvent());
        pipe.stop();

        // Doesn't shut down the pipeline properly so the video is lost.
    }
}