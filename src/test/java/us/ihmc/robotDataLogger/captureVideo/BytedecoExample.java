package us.ihmc.robotDataLogger.captureVideo;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameRecorder.Exception;

public class BytedecoExample
{
    final private static int WEBCAM_DEVICE_INDEX = 0;
    final private static int FRAME_RATE = 30;
    final private static int GOP_LENGTH_IN_FRAMES = 60;

    private static long startTime = 0;

    public static void main(String[] args) throws Exception, org.bytedeco.javacv.FrameGrabber.Exception
    {
        final int captureWidth = 1280;
        final int captureHeight = 720;

        try (VideoInputFrameGrabber grabber = new VideoInputFrameGrabber(WEBCAM_DEVICE_INDEX))
        {
            grabber.setImageWidth(captureWidth);
            grabber.setImageHeight(captureHeight);
            grabber.start();

            String filename = "ihmc-video-codecs/src/test/resources/recordedVideo.mp4";

            // RTMP url to an FMS / Wowza server
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filename, captureWidth, captureHeight))
            {
                recorder.setInterleaved(true);


                // decrease "startup" latency in FFMPEG (see:
                // https://trac.ffmpeg.org/wiki/StreamingGuide)
                recorder.setVideoOption("tune", "zerolatency");
                // tradeoff between quality and encode speed
                // possible values are ultrafast,superfast, veryfast, faster, fast,
                // medium, slow, slower, veryslow
                // ultrafast offers us the least amount of compression (lower encoder
                // CPU) at the cost of a larger stream size
                // at the other end, veryslow provides the best compression (high
                // encoder CPU) while lowering the stream size
                // (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
                recorder.setVideoOption("preset", "ultrafast");
                // Constant Rate Factor (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
                recorder.setVideoOption("crf", "28");
                // 2000 kb/s, reasonable "sane" area for 720
                recorder.setVideoBitrate(2000000);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
                recorder.setFormat("mp4");
                // FPS (frames per second)
                recorder.setFrameRate(FRAME_RATE);
                // Key frame interval, in our case every 2 seconds -> 30 (fps) * 2 = 60
                // (gop length)
                recorder.setGopSize(GOP_LENGTH_IN_FRAMES);

                recorder.start();

                // A really nice hardware accelerated component for our preview...
                final CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

                Frame capturedFrame;

                // Loop to capture a video, will stop after iterations have been completed
                int timer = 0;

                while (timer < 40)
                {
                    if ((capturedFrame = grabber.grab()) == null)
                    {
                        return;
                    }
                    if (cFrame.isVisible())
                    {
                        // Show our frame in the preview
                        cFrame.showImage(capturedFrame);
                    }

                    if (startTime == 0)
                    {
                        startTime = System.currentTimeMillis();
                    }

                    long videoTS = 1000 * (System.currentTimeMillis() - startTime);

                    if (videoTS > recorder.getTimestamp())
                    {
                        System.out.println("Lip-flap correction: " + videoTS + " : " + recorder.getTimestamp() + " -> " + (videoTS - recorder.getTimestamp()));

                        // We tell the recorder to write this frame at this timestamp
                        recorder.setTimestamp(videoTS);
                    }

                    // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
                    recorder.record(capturedFrame);

                    timer += 1;
                }

                cFrame.dispose();
                recorder.stop();
            }
            grabber.stop();
        }
    }
}