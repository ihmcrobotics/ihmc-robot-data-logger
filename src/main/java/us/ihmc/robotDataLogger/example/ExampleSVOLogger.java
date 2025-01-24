package us.ihmc.robotDataLogger.example;

import us.ihmc.commons.thread.RepeatingTaskThread;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.robotDataLogger.ZEDSDKAnnounce;
import us.ihmc.robotDataLogger.logger.ZEDSVOLoggerManager;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2NodeBuilder;
import us.ihmc.ros2.ROS2Publisher;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.library.ZEDJavaAPINativeLibrary;

import java.io.File;
import java.util.UUID;

import static us.ihmc.zed.global.zed.*;

/**
 * Starts a local USB ZED sensor, creates a ZEDSVOLoggerManager, and publishes on the ZED_SDK_ANNOUNCE_TOPIC topic
 * The ZEDSVOLoggerManager will listen to the ZED_SDK_ANNOUNCE_TOPIC and attempt to connect to the SVO stream to start
 * logging.
 */
public class ExampleSVOLogger
{
   private static volatile boolean running = true;
   private static final File LOG_DIRECTORY = new File(System.getProperty("user.home"),
                                                     "Desktop/svoLoggingExample/log" + UUID.randomUUID().toString().substring(0, 5));
   private static final ZEDSVOLoggerManager SVO_LOGGER_MANAGER = new ZEDSVOLoggerManager(LOG_DIRECTORY, LOG_DIRECTORY);

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
         Do nothing forever, everything else runs in other threads
       */
      ThreadTools.sleepForever();
   }

   public static void destroy()
   {
      running = false;

      SVO_LOGGER_MANAGER.destroy();

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
      sl_enable_streaming(0, SL_STREAMING_CODEC_H264, 8000, (short) 30000, -1, 0, 16084, 30);
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

      /*
         Publish the stream information on the ZED_SDK_ANNOUNCE_TOPIC topic
         so the logger knows how to connect
       */
      ROS2Node ros2Node = new ROS2NodeBuilder().build("zed_announce_node");
      ROS2Publisher<ZEDSDKAnnounce> publisher = ros2Node.createPublisher(ZEDSVOLoggerManager.ZED_SDK_ANNOUNCE_TOPIC);
      RepeatingTaskThread zedSDKAnnounceThread = new RepeatingTaskThread("ZEDSDKAnnounceThread", () ->
      {
         ZEDSDKAnnounce message = new ZEDSDKAnnounce();
         message.setAddress("127.0.0.1");
         message.setPort((short) 30000);
         publisher.publish(message);
      });
      zedSDKAnnounceThread.setFrequencyLimit(1.0);
      zedSDKAnnounceThread.startRepeating();
   }
}
