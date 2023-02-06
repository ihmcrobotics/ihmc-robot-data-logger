package us.ihmc.publisher.logger;

import java.awt.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.PlayBin;
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

        Bin videoBin = Gst.parseBinFromDescription(
                "appsink name=videoAppSink",
                true);

        Pipeline pipe = (Pipeline) Gst.parseLaunch(
                "decklinkvideosrc device-number=0 connection=sdi mode=auto " +
                "! videoconvert " +
                "! jpegenc " +
                "! avimux " +
                "! filesink location=/home/nick/decklinkCapture.mov");

        pipe.play();
        ThreadTools.sleepSeconds(10);
    }
}