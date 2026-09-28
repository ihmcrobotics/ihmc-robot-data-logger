package us.ihmc.robotDataLogger.logger;

import static us.ihmc.zed.global.zed.*;

import logger_msgs.ZEDSDKAnnounce;
import org.bytedeco.javacpp.BytePointer;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.exception.ExceptionTools;
import us.ihmc.commons.thread.RepeatingTaskThread;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.ZEDTools;
import us.ihmc.zed.global.zed;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects to a remote ZED SDK and logs an SVO file.
 * Manages an internal frame grab thread.
 */
public class ZEDSVOLogger
{
   private static final boolean TRANSCODE = false;
   private static final BytePointer ENCRYPTION_KEY = new BytePointer("");

   private static final float OPEN_TIMEOUT_SECONDS = 5.0f;
   private static final double GRAB_RETRY_DELAY_SECONDS = 3.0;
   private static final int MAX_CONSECUTIVE_GRAB_FAILURES = 3;
   private static final long GRAB_THREAD_JOIN_TIMEOUT_MILLIS = 3000;
   private static final long STATUS_PRINT_INTERVAL_MILLIS = 5000;

   // The ZED SDK only has MAX_CAMERA_PLUGIN camera instances, so IDs have to be handed back when a logger closes
   private static final boolean[] CAMERA_IDS_IN_USE = new boolean[MAX_CAMERA_PLUGIN];

   private final int cameraID = acquireCameraID();
   private SL_InitParameters initParameters;
   private SL_RuntimeParameters runtimeParameters;
   private final RepeatingTaskThread grabThread = new RepeatingTaskThread(getClass().getName() + "GrabThread" + cameraID, this::grab);

   private String name = "ZED " + cameraID;
   private String svoPrefix;
   private long controllerZeroInSensorFrame;
   private FileWriter timestampWriter;
   private int consecutiveFailedGrabs = 0;
   private String svoFileName = "";
   private long lastStatusPrintTimeMillis = 0;
   private long framesSinceLastStatusPrint = 0;
   private long currentFrameNumber = 0;

   private final AtomicBoolean closed = new AtomicBoolean(false);
   // Guarded by this. True while sl_open_camera is running, closing the camera during that isn't safe
   private boolean connecting = false;

   public ZEDSVOLogger()
   {
      // A grab stuck in native code must never keep the logger process alive
      grabThread.setDaemon(true);
   }

   /**
    * Opens the ZED SDK stream and starts recording. This can block for a long time inside the ZED SDK if the stream
    * isn't publishing, so it should not be called from a thread that anything else depends on.
    */
   public void connect(String svoFile, String datFile, String address, int port, int fps, int bitrate, long sensorTimestamp, long controllerTimestamp)
   {
      name = "ZED " + cameraID + " (" + address + ":" + port + ")";
      svoFileName = new File(svoFile).getName();

      synchronized (this)
      {
         if (closed.get())
            return;
         connecting = true;
      }

      try {
         String[] parts = svoFile.split("[/\\\\]");
         svoPrefix = parts[parts.length - 1].substring(0, "yyyyMMdd_HHmmss".length());
         timestampWriter = ExceptionTools.handle(() -> new FileWriter(datFile, true), DefaultExceptionHandler.RUNTIME_EXCEPTION);
      }
      catch (Exception ignored)
      {
         // If the svoFile is not in standard logger format
      }

      initParameters = new SL_InitParameters();
      initParameters.input_type(zed.SL_INPUT_TYPE_STREAM);
      initParameters.async_grab_camera_recovery(true);
      initParameters.open_timeout_sec(OPEN_TIMEOUT_SECONDS);

      runtimeParameters = new SL_RuntimeParameters();
      runtimeParameters.reference_frame(SL_REFERENCE_FRAME_CAMERA);
      runtimeParameters.enable_depth(false);

      controllerZeroInSensorFrame = sensorTimestamp - controllerTimestamp;

      LogTools.info("Connecting to ZED SDK stream on: " + address + ":" + port);

      if (sl_is_opened(cameraID))
         sl_close_camera(cameraID);

      int returnCode = sl_open_camera(cameraID, initParameters, 0, "", address, port, -1, "", "", "");
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("Could not connect to ZED SDK stream: " + ZEDTools.errorMessage(returnCode));

      returnCode = sl_enable_recording(cameraID, svoFile, SL_SVO_COMPRESSION_MODE_H264, bitrate, fps, TRANSCODE, ENCRYPTION_KEY, SL_SVO_ENCODING_PRESET_DEFAULT);
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("Could not enable SVO recording: " + ZEDTools.errorMessage(returnCode));

      boolean opened = sl_is_opened(cameraID);

      synchronized (this)
      {
         connecting = false;

         if (opened && !closed.get())
         {
            LogTools.info("Connected to ZED SDK stream on: " + address + ":" + port + ", recording to " + svoFileName);

            grabThread.startRepeating();
            return;
         }

         if (!opened)
            LogTools.error(name + " did not open, will try again on the next announce");
         else
            System.out.println(name + " was closed while it was connecting");

         // Mark it closed so the manager drops this logger and tries again on the next announce
         closed.set(true);
      }

      // close() either wasn't called or skipped the native cleanup because we were connecting, so do it here
      closeNative();
   }

   /**
    * Stops the grab thread and closes the ZED SDK camera. Safe to call more than once and from any thread,
    * only the first call does anything.
    */
   public void close()
   {
      synchronized (this)
      {
         if (!closed.compareAndSet(false, true))
            return;

         if (connecting)
         {
            // connect() cleans up once sl_open_camera returns
            System.out.println(name + " is still connecting, it will close when the connection attempt returns");
            return;
         }
      }

      // Can't use LogTools in here, we might be shutting down...
      System.out.println("Closing " + name);

      grabThread.kill();

      // When called from the grab thread (too many failed grabs) the thread stops on its own once grab() returns
      if (Thread.currentThread() != grabThread && grabThread.isAlive())
      {
         // Wakes the grab thread up if it's parked waiting to retry a grab
         grabThread.interrupt();
         ExceptionTools.handle(() -> grabThread.join(GRAB_THREAD_JOIN_TIMEOUT_MILLIS), DefaultExceptionHandler.PRINT_MESSAGE);

         if (grabThread.isAlive())
         {
            // Closing the camera while a grab is still running in native code isn't safe, so leave it
            // and keep its camera ID reserved. The grab thread is a daemon so it won't block the process from exiting.
            System.err.println(name + ": grab thread did not stop within " + GRAB_THREAD_JOIN_TIMEOUT_MILLIS + " ms, abandoning it without closing the camera");
            return;
         }
      }

      closeNative();
   }

   private void closeNative()
   {
      System.out.println(name + ": disabling recording");
      sl_disable_recording(cameraID);
      System.out.println(name + ": closing camera");
      sl_close_camera(cameraID);
      sl_unload_instance(cameraID);

      if (initParameters != null)
         initParameters.close();
      if (runtimeParameters != null)
         runtimeParameters.close();

      if (timestampWriter != null)
         ExceptionTools.handle(timestampWriter::close, DefaultExceptionHandler.PRINT_MESSAGE);

      releaseCameraID(cameraID);

      System.out.println("Closed " + name);
   }

   public void grab()
   {
      if (!closed.get())
      {
         int returnCode = sl_grab(cameraID, runtimeParameters);

         if (returnCode != SL_ERROR_CODE_SUCCESS)
         {
            // Don't write a timestamp, no frame was recorded so it would shift every following frame in the .dat file
            ++consecutiveFailedGrabs;

            if (consecutiveFailedGrabs >= MAX_CONSECUTIVE_GRAB_FAILURES)
            {
               // Stop retrying forever, the manager will reconnect on the next announce if the ZED comes back
               LogTools.error(name + " failed to grab " + consecutiveFailedGrabs + " times in a row (" + ZEDTools.errorMessage(returnCode)
                              + "), assuming it is disconnected");
               close();
               return;
            }

            LogTools.info(name + " could not grab an image (" + ZEDTools.errorMessage(returnCode) + "), trying again in a few seconds...");

            // Wait some time before trying to grab again, close() interrupts this
            ThreadTools.park(GRAB_RETRY_DELAY_SECONDS);
            return;
         }

         consecutiveFailedGrabs = 0;
         printStatusPeriodically();

         if (timestampWriter == null)
            return;

         try
         {
            // We assume both the sensor on board & controller real time thread clocks
            // run at the same speed to try an resolve delay and time stretching issues.
            // Here, controllerZeroInSensorFrame is calculated from two timestamps taken
            // on the robot at the moment both the sensor & controller are running
            long sensorTimestamp = sl_get_current_timestamp(cameraID);
            long controllerTimestamp = sensorTimestamp - controllerZeroInSensorFrame;
            timestampWriter.write("%d %d %s%n".formatted(controllerTimestamp, sensorTimestamp, svoPrefix));
         }
         catch (IOException ignored)
         {
         }
      }
   }

   private void printStatusPeriodically()
   {
      ++framesSinceLastStatusPrint;
      ++currentFrameNumber;

      long currentTimeMillis = System.currentTimeMillis();
      long millisSinceLastPrint = currentTimeMillis - lastStatusPrintTimeMillis;
      if (millisSinceLastPrint >= STATUS_PRINT_INTERVAL_MILLIS)
      {
         // Skip the first one, there's no interval to report on yet
         if (lastStatusPrintTimeMillis > 0)
         {
            LogTools.info("%s: current frame: %d".formatted(name, framesSinceLastStatusPrint, currentFrameNumber));
         }

         lastStatusPrintTimeMillis = currentTimeMillis;
         framesSinceLastStatusPrint = 0;
      }
   }

   public void synchronize(ZEDSDKAnnounce message)
   {
      controllerZeroInSensorFrame = message.getSensorTimestamp() - message.getControllerTimestamp();
   }

   public boolean isClosed()
   {
      return closed.get();
   }

   public String getName()
   {
      return name;
   }

   private static synchronized int acquireCameraID()
   {
      for (int i = 0; i < CAMERA_IDS_IN_USE.length; i++)
      {
         if (!CAMERA_IDS_IN_USE[i])
         {
            CAMERA_IDS_IN_USE[i] = true;
            return i;
         }
      }

      throw new IllegalStateException("All " + MAX_CAMERA_PLUGIN + " ZED SDK camera instances are in use");
   }

   private static synchronized void releaseCameraID(int cameraID)
   {
      CAMERA_IDS_IN_USE[cameraID] = false;
   }
}
