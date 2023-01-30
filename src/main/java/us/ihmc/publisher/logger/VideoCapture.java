package us.ihmc.publisher.logger;

import com.martiansoftware.jsap.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameRecorder.Exception;
import us.ihmc.javadecklink.Capture;

import java.util.ArrayList;

// There is currently no audio capture in this class
public class VideoCapture
{
   private static final boolean VIEW_FRAME = false;
   public static ArrayList<Long> timestampList = new ArrayList<>();

   static CanvasFrame cFrame = null;

   final private static int WEBCAM_DEVICE_INDEX = 0;
   final private static int FRAME_RATE = 60;

   private static long startTime = 0;

   public static void main(String[] args) throws Exception, org.bytedeco.javacv.FrameGrabber.Exception, InterruptedException, JSAPException
   {
      SimpleJSAP jsap = new SimpleJSAP("Capture test",
                                       "Test capture of one or more video capture card",
                                       new Parameter[] {
                                             new FlaggedOption("codec",
                                                               JSAP.STRING_PARSER,
                                                               "MJPEG",
                                                               JSAP.NOT_REQUIRED,
                                                               'c',
                                                               "codec",
                                                               "Codec either: H264 or MJPEG"),
                                             new FlaggedOption("outputPath",
                                                               JSAP.STRING_PARSER,
                                                               "",
                                                               JSAP.NOT_REQUIRED,
                                                               'p',
                                                               "path",
                                                               "Path to directory where video file(s) are to be saved, expected to end with '/'."),
                                             new FlaggedOption("crf",
                                                               JSAP.INTEGER_PARSER,
                                                               String.valueOf(23),
                                                               JSAP.NOT_REQUIRED,
                                                               'r',
                                                               "crf",
                                                               "CRF (Constant rate factor) for H264. 0-51, 0 is lossless. Sane values are 18 to 28."),
                                             new FlaggedOption("frameWidth",
                                                               JSAP.INTEGER_PARSER,
                                                               String.valueOf(1280),
                                                               JSAP.NOT_REQUIRED,
                                                               'w',
                                                               "frameWidth",
                                                               "Frame width"),
                                             new FlaggedOption("frameHeight",
                                                               JSAP.INTEGER_PARSER,
                                                               String.valueOf(720),
                                                               JSAP.NOT_REQUIRED,
                                                               'h',
                                                               "frameHeight",
                                                               "Frame Height"),
                                             new FlaggedOption("frameRate",
                                                               JSAP.INTEGER_PARSER,
                                                               String.valueOf(120),
                                                               JSAP.NOT_REQUIRED,
                                                               'f',
                                                               "frameRate",
                                                               "Frame rate of stuff"),
                                             new FlaggedOption("captureDuration",
                                                               JSAP.INTEGER_PARSER,
                                                               "5000",
                                                               JSAP.NOT_REQUIRED,
                                                               'd',
                                                               "duration",
                                                               "Capture duration in milliseconds for each capture card"),
                                             new FlaggedOption("decklinkId",
                                                               JSAP.INTEGER_PARSER,
                                                               String.valueOf(0),
                                                               JSAP.NOT_REQUIRED,
                                                               'i',
                                                               "Id",
                                                               "ID of the capture card to test")});
      JSAPResult config = jsap.parse(args);
      if (jsap.messagePrinted())
      {
         System.out.println(jsap.getUsage());
         System.out.println(jsap.getHelp());
         System.exit(-1);
      }

      int firstId = config.getInt("decklinkId");
      String outputPath = config.getString("outputPath");
      int duration = config.getInt("captureDuration");
      Capture.CodecID codec = config.getString("codec").contains("264") ? Capture.CodecID.AV_CODEC_ID_H264 : Capture.CodecID.AV_CODEC_ID_MJPEG;

      // This is the size of the finalized video, proportions need to be right
      final int captureWidth = config.getInt("frameWidth");
      final int captureHeight = config.getInt("frameHeight");

      System.out.println("Entering grabber try catch");
      try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("/dev/blackmagic/io0"))// + Integer.toString(firstId)))
      {
         System.out.println("INSIDE grabber try catch");
//         grabber.setImageWidth(captureWidth);
//         grabber.setImageHeight(captureHeight);
//         grabber.setFormat("H264");
         grabber.setTimeout(1);
         System.out.println("Starting stupid thing");
         grabber.start();

//         String filename = "C:/Users/nadiaocu/eclipse-java-17-ws/repository-group/ihmc-video-codecs/src/test/resources/recordedVideo.mov";
//         String filename = "ihmc-robot-data-logger/src/test/resources/recordedVideo.mov";
         String filename = "/home/shadylady/recordVideo.mov";

         // RTMP url to an FMS / Wowza server
         System.out.println("Entering Recorder try catch");
         try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filename, captureWidth, captureHeight))
         {
            System.out.println("Inside loop Recorder try catch");

            recorder.setVideoOption("crf", Integer.toString(config.getInt("crf")));
            recorder.setVideoOption("tune", "zerolatency");
//            recorder.setVideoOption("coder", "vlc");
//            recorder.setVideoBitrate(200000);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setFormat("mov");
            recorder.setFrameRate(config.getInt("frameRate"));

            recorder.start();

            if (VIEW_FRAME)
            {
               cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());
            }

            int timer = 0;
            Frame capturedFrame;

            // Loop to capture a video, will stop after iterations have been completed
            while (timer < duration && ((capturedFrame = grabber.grabAtFrameRate()) != null))
            {
               // Shows the captured frame its currently recording
               if (VIEW_FRAME)
               {
                  if (cFrame.isVisible())
                  {
                     cFrame.showImage(capturedFrame);
                  }
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

                  recorder.setTimestamp(videoTS);
               }

               // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
               recorder.record(capturedFrame);
               recordTimestampToArray(System.nanoTime(), timer + 1);

               timer += 1;
            }

            Thread.sleep(2000);

            if (VIEW_FRAME)
            {
               cFrame.dispose();
            }

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