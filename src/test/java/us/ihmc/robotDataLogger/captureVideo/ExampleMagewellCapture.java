package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import us.ihmc.log.LogTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This example class provides the basics for capturing a video using the ByteDeco JavaCV bindings
 * You need a decklink capture card, and a camera attached on the other end for this to work
 * The camera settings get overridden, so it doesn't matter what video mode or FPS its set too.
 * This only works on Windows software (Windows 11 and 10 have been tested)
 * This example works with either SDI or HDMI as the camera cable
 */

public class ExampleMagewellCapture
{
    private static final int WEBCAM_DEVICE_INDEX = 0;
    private static long startTime = 0;

    private static final String windowsBytedeco = "windowsBytedeco";

    public static String videoPath;
    public static String timestampPath;
    private static FileWriter timestampWriter;

    public static File videoFile;
    public static File timestampFile;

    public static void main(String[] args) throws InterruptedException
    {
        videoPath  =  "ihmc-robot-data-logger/out/" + windowsBytedeco + "_Video.mov";
        timestampPath = "ihmc-robot-data-logger/out/" + windowsBytedeco + "_Timestamps.dat";

        videoFile = new File(videoPath);
        timestampFile = new File(timestampPath);

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
                // Trying these settings for now (H264 is a bad setting because of slicing)
                recorder.setVideoOption("tune", "zerolatency");
                recorder.setFormat("mov");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.setFrameRate(60);

                recorder.setVideoOption("strict", "-2");

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

                    recorder.record(capturedFrame);
                    // System.nanoTime() represents the controllerTimestamp in this example since its fake
                    writeTimestampToFile(System.nanoTime(), recorder.getTimestamp());

                    timer++;
                }

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
}