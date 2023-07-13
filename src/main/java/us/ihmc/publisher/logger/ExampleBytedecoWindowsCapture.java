package us.ihmc.publisher.logger;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameRecorder.Exception;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * This example class provides the basics for capturing a video using the ByteDeco JavaCV bindings
 * You need a decklink capture card, and a camera attacked on the other end for this to work
 * The camera settings get overridden so it doesn't matter waht video mode or FPS its set too
 * This only works on Windows software and has been optimized for Windows 11
 */

public class ExampleBytedecoWindowsCapture
{
    private static final int WEBCAM_DEVICE_INDEX = 0;

    private static long startTime = 0;

    public static String videoPath = "ihmc-robot-data-logger/out/windowsBytedecoVideo.mov";
    public static final String timestampPath = "ihmc-robot-data-logger/out/windowsBytedecoTimestamps.dat";
    private static FileWriter timestampWriter;

    public static File videoFile = new File(videoPath);
    public static File timestampFile = new File(timestampPath);

    public static void main(String[] args) throws Exception, org.bytedeco.javacv.FrameGrabber.Exception, InterruptedException, MalformedURLException
    {
        // This is the resolution of the video, overrides camera
        final int captureWidth = 1920;
        final int captureHeight = 1080;

        // Can try VideoInputFrameGrabber as well to see how that one works
        try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX))
        {
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            grabber.start();

            setupTimestampWriter();

            // RTMP url to an FMS / Wowza server
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(videoFile, captureWidth, captureHeight))
            {
                //Trying these settings for now (H264 is a bad setting because of slicing)
                recorder.setVideoOption("tune", "zerolatency");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
                recorder.setFormat("mov");
                recorder.setFrameRate(60);

                // Start the recording piece of equipment, webcam and decklink work
                recorder.start();

                // A really nice hardware accelerated component for our preview...
                final CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

                int timer = 0;
                Frame capturedFrame;

                // Loop to capture a video, will stop after iterations have been completed
                while (timer < 250 && ((capturedFrame = grabber.grabAtFrameRate()) != null))
                {
                    // Shows the captured frame its currently recording
                    if (cFrame.isVisible())
                    {
                        cFrame.showImage(capturedFrame);
                    }

                    // Keeps track of time for the recorder because often times it gets offset
                    if (startTime == 0)
                        startTime = System.currentTimeMillis();

                    long videoTS = 1000 * (System.currentTimeMillis() - startTime);

                    if (videoTS > recorder.getTimestamp())
                    {
                        if (timer % 100 == 0)
                            System.out.println("Timer: " + timer + " Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));

                        // We tell the recorder to write this frame at this timestamp
                        recorder.setTimestamp(videoTS);
                    }

                    // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
                    recorder.record(capturedFrame);
                    writeTimestampToFile(System.nanoTime(), recorder.getTimestamp());

                    timer++;
                }

                Thread.sleep(2000);
                cFrame.dispose();
                recorder.flush();
                recorder.stop();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            //Stop running the recorder and the frame grabber
            grabber.stop();
            timestampWriter.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
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

    public static void writeTimestampToFile(long robotTimestamp, long pts) throws IOException
    {
        try
        {
            timestampWriter.write(robotTimestamp + " " + pts + "\n");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}