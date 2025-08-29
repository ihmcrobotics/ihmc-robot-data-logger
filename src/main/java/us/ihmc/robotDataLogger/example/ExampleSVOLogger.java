package us.ihmc.robotDataLogger.example;

import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.robotDataLogger.logger.ZEDSVOLogger;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.library.ZEDJavaAPINativeLibrary;

import java.util.UUID;

import static us.ihmc.zed.global.zed.*;

/**
 * Starts a local USB ZED sensor, and a ZEDSVOLogger which connects and logs to SVO
 */
public class ExampleSVOLogger
{
   private static final String ADDRESS = "127.0.0.1";
   private static final int PORT = 30000;

   private static final ZEDSVOLogger SVO_LOGGER = new ZEDSVOLogger();

   private static volatile boolean running = true;

   static
   {
      /*
        Load the zed-java-api library
        https://github.com/ihmcrobotics/zed-java-api
       */
      ZEDJavaAPINativeLibrary.load();

      /*
         Shutdown hook to close the local ZED sensor
       */
      Runtime.getRuntime().addShutdownHook(new Thread(ExampleSVOLogger::destroy));
   }

   public static void main(String[] args)
   {
      /*
         Start the local USB ZED sensor
       */
      startLocalUSBSensor();

      /*
         Connect and start logging the SVO
       */
      String path = System.getProperty("user.home") + "/Desktop/test" + UUID.randomUUID().toString().substring(0, 5);
      String svoFile = path + ".svo2";
      String datFile = path + ".dat";
      SVO_LOGGER.connect(svoFile, datFile, ADDRESS, PORT, 15, 8000, 0L, 0L);

      /*
         Do nothing forever, everything else runs in other threads
       */
      ThreadTools.sleepForever();
   }

   public static void destroy()
   {
      running = false;

      SVO_LOGGER.close();

      stopLocalUSBSensor();
   }

   private static void stopLocalUSBSensor()
   {
      /*
         All we need to do to stop the USB ZED sensor is call sl_close_camera
       */
      sl_close_camera(0);
   }

   private static void startLocalUSBSensor()
   {
      /*
         Set up the USB ZED
       */
      sl_create_camera(0);
      SL_InitParameters initParameters = new SL_InitParameters();
      initParameters.camera_fps(30);
      initParameters.resolution(SL_RESOLUTION_HD720);
      initParameters.input_type(SL_INPUT_TYPE_USB);
      initParameters.camera_device_id(0);
      int state = sl_open_camera(0, initParameters, 0, "", "", 0, "", "", "");
      sl_enable_streaming(0, SL_STREAMING_CODEC_H264, 8000, (short) PORT, -1, 0, 16084, 30);
      if (state != 0)
         throw new RuntimeException("Could not initialize ZED");

      /*
         Start grabbing images in a thread
       */
      SL_RuntimeParameters runtimeParameters = new SL_RuntimeParameters();
      runtimeParameters.enable_depth(true);
      Thread imageGrabThread = new Thread(() ->
      {
         while (running)
         {
            /*
               Grab the image and do nothing with it
             */
            sl_grab(0, runtimeParameters);
         }
      }, "ImageGrabThread");
      imageGrabThread.start();
   }
}
