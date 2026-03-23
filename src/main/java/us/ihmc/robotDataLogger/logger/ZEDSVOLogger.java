package us.ihmc.robotDataLogger.logger;

import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.exception.ExceptionTools;
import us.ihmc.commons.thread.RepeatingTaskThread;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.ZEDSDKAnnounce;
import us.ihmc.zed.SL_InitParameters;
import us.ihmc.zed.SL_RuntimeParameters;
import us.ihmc.zed.ZEDTools;
import us.ihmc.zed.global.zed;

import java.io.FileWriter;
import java.io.IOException;

import static us.ihmc.zed.global.zed.*;

/**
 * Connects to a remote ZED SDK and logs an SVO file.
 * Manages an internal frame grab thread.
 */
public class ZEDSVOLogger
{
   private static final boolean TRANSCODE = false;

   private static int nextCameraId = 0;

   private final int cameraID = nextCameraId++;
   private SL_InitParameters initParameters;
   private SL_RuntimeParameters runtimeParameters;
   private final RepeatingTaskThread grabThread = new RepeatingTaskThread(getClass().getName() + "GrabThread", this::grab);

   private String svoPrefix;
   private long controllerZeroInSensorFrame;
   private FileWriter timestampWriter;

   private volatile boolean closed;

   public void connect(String svoFile, String datFile, String address, int port, int fps, int bitrate, long sensorTimestamp, long controllerTimestamp)
   {
      closed = false;

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

      runtimeParameters = new SL_RuntimeParameters();
      runtimeParameters.reference_frame(SL_REFERENCE_FRAME_CAMERA);
      runtimeParameters.enable_depth(false);

      controllerZeroInSensorFrame = sensorTimestamp - controllerTimestamp;

      LogTools.info("Connecting to ZED SDK stream on: " + address + ":" + port);

      if (sl_is_opened(cameraID))
         sl_close_camera(cameraID);

      int returnCode = sl_open_camera_from_stream(cameraID, initParameters, address, port, "", "", "");
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("Could not connect to ZED SDK stream: " + ZEDTools.errorMessage(returnCode));

      returnCode = sl_enable_recording(cameraID, svoFile, SL_SVO_COMPRESSION_MODE_H264, bitrate, fps, TRANSCODE);
      if (returnCode != SL_ERROR_CODE_SUCCESS)
         LogTools.error("Could not enable SVO recording: " + ZEDTools.errorMessage(returnCode));

      if (sl_is_opened(cameraID))
      {
         LogTools.info("Connected to ZED SDK stream on: " + address + ":" + port);

         grabThread.startRepeating();
      }
   }

   public void close()
   {
      if (!closed)
      {
         closed = true;

         grabThread.blockingKill();

         sl_close_camera(cameraID);
         sl_unload_instance(cameraID);

         initParameters.close();
         runtimeParameters.close();

         // Can't use LogTools here, we might be shutting down...
         System.out.println("Closing ZED SDK stream");

         ExceptionTools.handle(timestampWriter::close, DefaultExceptionHandler.PRINT_MESSAGE);
      }
   }

   public void grab()
   {
      if (!closed)
      {
         int returnCode = sl_grab(cameraID, runtimeParameters);

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

         if (returnCode != SL_ERROR_CODE_SUCCESS)
         {
            // Wait some time before trying to grab again
            ThreadTools.park(5.0);

            LogTools.info("Could not grab image from ZED, trying again in a few seconds...");
         }
      }
   }

   public void synchronize(ZEDSDKAnnounce message)
   {
      controllerZeroInSensorFrame = message.getSensorTimestamp() - message.getControllerTimestamp();
   }

   public boolean isClosed()
   {
      return closed;
   }
}
