package us.ihmc.robotDataLogger.logger;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.exception.ExceptionTools;
import us.ihmc.commons.thread.RepeatingTaskThread;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.global.zed;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;

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

   private static int nextCameraId = 10;

   private final int cameraID = nextCameraId++;
   private SL_InitParameters initParameters;
   private SL_RuntimeParameters runtimeParameters;
   private final RepeatingTaskThread grabThread = new RepeatingTaskThread(getClass().getName() + "GrabThread", this::grab);
   private final RepeatingTaskThread connectionWatchdogThread = new RepeatingTaskThread(getClass().getName() + "ConnectionWatchdog", this::connectionCheck);

   private String svoFileName;
   private LongSupplier timestampSupplier;
   private FileWriter timestampWriter;
   private Instant startTime;

   private volatile double lastGrabTime;
   private volatile boolean stopRequested;
   private volatile boolean completelyStopped;
   private volatile boolean failedBeyondRecovery;

   public void start(String svoFile, String datFile, LongSupplier timestampSupplier, String address, int port)
   {
      this.svoFileName = svoFile;
      this.timestampSupplier = timestampSupplier;

      if (stopRequested)
         throw new IllegalStateException("Cannot restart ZEDSVOLogger once stopped");

      timestampWriter = ExceptionTools.handle(() -> new FileWriter(datFile, true), DefaultExceptionHandler.RUNTIME_EXCEPTION);

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

         startTime = Instant.now();

         grabThread.setFrequencyLimit(RepeatingTaskThread.UNLIMITED_FREQUENCY);
         grabThread.startRepeating();
      }

      connectionWatchdogThread.setFrequencyLimit(Conversions.secondsToHertz(CONNECT_TIMEOUT));
      ThreadTools.startAThread(() ->
      {
         ThreadTools.park(5.0);
         connectionWatchdogThread.startRepeating();
      }, getClass().getSimpleName() + "ConnectionWatchdogDelay");
   }

   public void stop()
   {
      if (!stopRequested)
      {
         stopRequested = true;

         grabThread.blockingKill();
         connectionWatchdogThread.kill();

         sl_close_camera(cameraID);
         sl_unload_instance(cameraID);

         initParameters.close();
         runtimeParameters.close();

         // Can't use LogTools here, we might be shutting down...
         System.out.println("Closing ZED SDK stream");

         ExceptionTools.handle(timestampWriter::close, DefaultExceptionHandler.PRINT_MESSAGE);

         completelyStopped = true;
      }
   }

   public void grab()
   {
      if (stopRequested)
         throw new IllegalStateException("Cannot grab(), already stopped");

      int returnCode = sl_grab(cameraID, runtimeParameters);

      lastGrabTime = System.currentTimeMillis() / 1000D;
      long frameTimeNanos = Duration.between(startTime, Instant.now()).toNanos();

      try
      {
         timestampWriter.write("%d %d %s%n".formatted(timestampSupplier.getAsLong(), frameTimeNanos, svoFileName));
      }
      catch (IOException e)
      {
         LogTools.error(e.getMessage());
      }

      if (returnCode == SL_ERROR_CODE_FAILURE || returnCode == SL_ERROR_CODE_CAMERA_NOT_DETECTED)
         failedBeyondRecovery = true;

      if (returnCode == SL_ERROR_CODE_CAMERA_NOT_INITIALIZED || returnCode == SL_ERROR_CODE_CAMERA_REBOOTING)
         stop();
   }

   private void connectionCheck()
   {
      if (!stopRequested())
      {
         if (!sl_is_opened(cameraID))
         {
            LogTools.info("Unable to connect to ZED SDK stream");

            stop();
         }

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
   public boolean stopRequested()
   {
      return stopRequested;
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
