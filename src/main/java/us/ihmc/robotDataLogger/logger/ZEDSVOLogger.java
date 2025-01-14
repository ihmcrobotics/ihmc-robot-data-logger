package us.ihmc.robotDataLogger.logger;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.RepeatingTaskThread;
import us.ihmc.log.LogTools;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.global.zed;

import static us.ihmc.zed.global.zed.*;

/**
 * Connects to a remote ZED SDK and logs an SVO file.
 * Manages an internal frame grab thread.
 */
public class ZEDSVOLogger
{
   private static final double CONNECT_TIMEOUT = 2.0;
   private static final int BITRATE = 8000;
   private static final int MAX_FPS = 15;
   private static final boolean TRANSCODE = false;

   private static int nextCameraId = 0;

   private final int cameraID = nextCameraId++;
   protected SL_InitParameters initParameters;
   protected SL_RuntimeParameters runtimeParameters;
   private final RepeatingTaskThread grabThread = new RepeatingTaskThread(getClass().getName() + "GrabThread", this::grab);
   private final RepeatingTaskThread connectionWatchdogThread = new RepeatingTaskThread(getClass().getName() + "ConnectionWatchdog", this::connectionCheck);

   private volatile double lastGrabTime;
   private volatile boolean stopped;
   private volatile boolean completelyStopped;
   private volatile boolean failedBeyondRecovery;

   public void start(String svoFile, String address, int port)
   {
      if (stopped)
         throw new IllegalStateException("Cannot restart ZEDSVOLogger once stopped");

      initParameters = new SL_InitParameters();
      initParameters.input_type(zed.SL_INPUT_TYPE_STREAM);
      initParameters.async_grab_camera_recovery(true);

      runtimeParameters = new SL_RuntimeParameters();
      runtimeParameters.reference_frame(SL_REFERENCE_FRAME_CAMERA);
      runtimeParameters.enable_depth(false);

      LogTools.info("Connecting to ZED SDK stream on: " + address + ":" + port);

      if (sl_is_opened(cameraID))
         sl_close_camera(cameraID);

      int returnCode = sl_open_camera(cameraID, initParameters, 0, "", address, port, "", "", "");
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("ZED SDK error code: " + returnCode);

      returnCode = sl_enable_recording(cameraID, svoFile, SL_SVO_COMPRESSION_MODE_H264, BITRATE, MAX_FPS, TRANSCODE);
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("ZED SDK error code: " + returnCode);

      if (sl_is_opened(cameraID))
      {
         LogTools.info("Connected to ZED SDK stream on: " + address + ":" + port);

         grabThread.setFrequencyLimit(RepeatingTaskThread.UNLIMITED_FREQUENCY);
         grabThread.startRepeating();
      }

      connectionWatchdogThread.setFrequencyLimit(Conversions.secondsToHertz(CONNECT_TIMEOUT));
      connectionWatchdogThread.startRepeating();
   }

   public void stop()
   {
      if (!stopped)
      {
         stopped = true;

         grabThread.blockingKill();

         sl_close_camera(cameraID);
         sl_unload_instance(cameraID);

         initParameters.close();
         runtimeParameters.close();

         // Can't use LogTools here, we might be shutting down...
         System.out.println("Closing ZED SDK stream");

         completelyStopped = true;
      }
   }

   public void grab()
   {
      if (stopped)
         throw new IllegalStateException("Cannot grab(), already stopped");

      int returnCode = sl_grab(cameraID, runtimeParameters);

      lastGrabTime = System.currentTimeMillis() / 1000D;

      if (returnCode == SL_ERROR_CODE_FAILURE || returnCode == SL_ERROR_CODE_CAMERA_NOT_DETECTED)
         failedBeyondRecovery = true;

      if (returnCode == SL_ERROR_CODE_CAMERA_NOT_INITIALIZED || returnCode == SL_ERROR_CODE_CAMERA_REBOOTING)
         stop();
   }

   private void connectionCheck()
   {
      if (!sl_is_opened(cameraID))
      {
         LogTools.info("Unable to connect to ZED SDK stream");

         stop();
      }

      while (!stopped)
      {
         if ((System.currentTimeMillis() / 1000D) - lastGrabTime > CONNECT_TIMEOUT)
         {
            LogTools.info("grab() timeout reached, disconnecting from ZED SDK stream");

            stop();
         }
      }
   }

   /**
    * @return true if stop() has been called, false if not
    */
   public boolean stopped()
   {
      return stopped;
   }

   /**
    * @return true if ZED SDK has completely closed the camera and stop() has completely finished
    */
   public boolean completelyStopped()
   {
      return completelyStopped;
   }

   /**
    * @return true if we received an error code from ZED SDK that is likely to cause a crash
    */
   public boolean failedBeyondRecovery()
   {
      return failedBeyondRecovery;
   }
}
