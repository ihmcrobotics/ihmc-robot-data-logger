package us.ihmc.publisher.logger;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameRecorder.Exception;
import us.ihmc.robotDataLogger.logger.VideoIn;

import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;

// There is currently no audio capture in this class
public class WindowsVideoCapture
{

    public static ArrayList<Long> timestampList = new ArrayList<>();

    final private static int WEBCAM_DEVICE_INDEX = 0;
    final private static int FRAME_RATE = 60;

    private static long startTime = 0;

    public static void main(String[] args) throws Exception, org.bytedeco.javacv.FrameGrabber.Exception, InterruptedException, MalformedURLException
    {
        // This is the size of the finalized video, proportions need to be right
        final int captureWidth = 1280;
        final int captureHeight = 720;

//        try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX))
        try (VideoInputFrameGrabber grabber = new VideoInputFrameGrabber(0))
//        try (VideoInputFrameGrabber grabber = new VideoInputFrameGrabber(0))
//        try (VideoInputFrameGrabber grabber = new VideoInputFrameGrabber(0))
        {
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            grabber.start();

//         String filename = "C:/Users/nadiaocu/eclipse-java-17-ws/repository-group/ihmc-video-codecs/src/test/resources/recordedVideo.mov";
            String filename = "ihmc-robot-data-logger/src/test/resources/recordedVideo.mov";

            // RTMP url to an FMS / Wowza server
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filename, captureWidth, captureHeight))
            {
                recorder.setVideoOption("crf", "1");
//            recorder.setVideoOption("coder", "vlc");
                recorder.setVideoOption("tune", "zerolatency");
//            recorder.setVideoBitrate(200000);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
                recorder.setFormat("mov");
                recorder.setFrameRate(120);
//            recorder.setGopSize(59.94);

                // Start the recording piece of equipment, webcam and decklink work
                recorder.start();

                // A really nice hardware accelerated component for our preview...
                final CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

                int timer = 0;
                Frame capturedFrame;

                // Loop to capture a video, will stop after iterations have been completed
                while (timer < 250 && ((capturedFrame = grabber.grabAtFrameRate()) != null))
                {
//               Thread.sleep(80);
                    // Shows the captured frame its currently recording
                    if (cFrame.isVisible())
                    {
                        cFrame.showImage(capturedFrame);
                    }

                    // Keeps track of time for the recorder because often times it gets offset
                    if (startTime == 0)
                    {
                        startTime = System.currentTimeMillis();
                    }

                    long videoTS = 1000 * (System.currentTimeMillis() - startTime);


//               System.out.print(videoTS + " -- > --");
//               System.out.println(recorder.getTimestamp());
                    if (videoTS > recorder.getTimestamp())
                    {
                        System.out.println("Timer: " + timer + " Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));

//                  System.out.println(timer);
                        // We tell the recorder to write this frame at this timestamp
                        recorder.setTimestamp(videoTS);
                    }
//               recorder.setTimestamp(System.currentTimeMillis() - startTime);

                    recorder.record(capturedFrame);
                    recordTimestampToArray(System.nanoTime(), timer + 1);
                    // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
//               recorder.record(capturedFrame);

                    timer += 1;
                }

                Thread.sleep(2000);
                cFrame.dispose();
                recorder.flush();
                recorder.stop();
            }
            //Stop running the recorder and the frame grabber
            grabber.stop();
        }

        System.out.println("Printing timestamp file");
        Thread.sleep(2000);
        printArray();
    }


    public static void recordTimestampToArray(long robotTimestamp, int index)
    {
        timestampList.add(robotTimestamp);
        timestampList.add(index * 1001L);
    }

    public static void printArray()
    {
        System.out.println("1");
        System.out.println("60000");
        for (int i = 0; i < timestampList.size() - 1; i++)
        {
            System.out.println(timestampList.get(i) + " " + timestampList.get(i + 1));
            i++;
        }
    }
}