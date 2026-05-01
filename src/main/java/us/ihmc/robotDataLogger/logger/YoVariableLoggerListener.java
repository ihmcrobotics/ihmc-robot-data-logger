package us.ihmc.robotDataLogger.logger;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.MathTools;
import us.ihmc.idl.serializers.extra.YAMLSerializer;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.*;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.rtps.LogParticipantSettings;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerDescription;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;
import us.ihmc.tools.compression.SnappyUtils;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdException;
import us.ihmc.yoVariables.variable.YoVariable;

import us.ihmc.concurrent.ConcurrentRingBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class YoVariableLoggerListener implements YoVariablesUpdatedListener
{
   /**
    * We wait this long before shutting down the logger, this prevents logging forever in the case where the server didn't
    * shut down properly.
    */
   private static final int TICKS_WITHOUT_DATA_BEFORE_SHUTDOWN = 5000;
   /**
    * Create a buffer ring of 8 buffers. This keeps data in case the logger is slow.
    */
   private static final int BATCH_RING_BUFFER_SIZE = 8;

   private static final int FLUSH_EVERY_N_PACKETS = 250;
   private static final int COMPRESSION_BATCH_SIZE = 10;
   private static final int ZSTD_COMPRESSION_LEVEL = 1;
   public static final long STATUS_PACKET_RATE = Conversions.secondsToNanoseconds(5.0);
   private static final long VIDEO_RECORDING_TIMEOUT = Conversions.secondsToNanoseconds(1.0);

   public static final String propertyFile = propertyFileNameBuilder(0);
   private static final String handshakeFilename = "handshake.yaml";
   private static final String dataFilename = "robotData.bsz";
   private static final String modelFilename = "model.sdf";
   private static final String modelResourceBundle = "resources.zip";
   private static final String indexFilename = "robotData.dat";
   private static final String summaryFilename = "summary.csv";

   private final Object synchronizer = new Object();
   private final Object timestampUpdater = new Object();

   private final boolean flushAggressivelyToDisk;

   private final File tempDirectory;
   private final File finalDirectory;
   private final boolean disableVideo;
   private final YoVariableLoggerOptions options;
   private FileChannel dataChannel;
   private FileChannel indexChannel;

   // Background compression thread — owns these exclusively (never touched by RT thread)
   private final ByteBuffer indexBuffer = ByteBuffer.allocate(16);
   private ByteBuffer compressedBuffer;
   private Thread compressionThread;
   private volatile boolean compressionThreadRunning = false;

   // Batch state — written by RT thread, consumed by compression thread via batchRing
   private ConcurrentRingBuffer<ByteBuffer> batchRingBuffer;
   private ByteBuffer currentBatchBuffer = null;
   private int batchTickCount = 0;

   private volatile boolean connected = false;

   private final LogPropertiesWriter logProperties;
   private ArrayList<VideoDataLoggerInterface> videoDataLoggers = new ArrayList<>();

   private final ArrayList<CameraConfiguration> cameras = new ArrayList<>();

   private boolean clearingLog = false;

   private long currentIndex = 0;

   private long lastReceivedTimestamp = Long.MIN_VALUE;
   private long ticksWithoutNewTimestamp = 0;
   private boolean alreadyShutDown = false;

   private final Announcement request;
   private final Consumer<Announcement> doneListener;

   private YoVariableSummarizer yoVariableSummarizer = null;

   // Reconstruction variables for disk data format
   private YoVariable[] variables;
   private JointState[] jointStates;
   private ByteBuffer dataBuffer;
   private LongBuffer dataBufferAsLong;
   private long[] dataAsLong;
   private long[] cachedJointStateValues;

   private YoVariableClientInterface yoVariableClientInterface = null;

   private long lastStatusUpdateTimestamp = 0;
   private long logStartedTimestamp = 0;

   public YoVariableLoggerListener(File tempDirectory, File finalDirectory, String timestamp, Announcement request)
   {
      this(tempDirectory, finalDirectory, timestamp, request, null, null, (t) ->
      {
      });
   }

   public YoVariableLoggerListener(File tempDirectory,
                                   File finalDirectory,
                                   String timestamp,
                                   Announcement request,
                                   HTTPDataServerDescription target,
                                   YoVariableLoggerOptions options,
                                   Consumer<Announcement> doneListener)
   {
      LogTools.debug(toString(request));
      this.tempDirectory = tempDirectory;
      this.finalDirectory = finalDirectory;

      this.request = request;
      this.doneListener = doneListener;

      this.options = options;
      if (options == null)
      {
         this.disableVideo = true;
         this.flushAggressivelyToDisk = false;
      }
      else
      {
         this.disableVideo = options.getDisableVideo();
         this.flushAggressivelyToDisk = options.isFlushAggressivelyToDisk();
      }

      logProperties = new LogPropertiesWriter(new File(tempDirectory, propertyFile));
      logProperties.getVariables().setHandshake(handshakeFilename);
      logProperties.getVariables().setData(dataFilename);
      logProperties.getVariables().setCompressed(true);
      logProperties.getVariables().setTimestamped(true);
      logProperties.getVariables().setCompressionBatchSize(COMPRESSION_BATCH_SIZE);
      logProperties.getVariables().setCompressionType("zstd");
      logProperties.getVariables().setIndex(indexFilename);
      logProperties.getVariables().setHandshakeFileType(HandshakeFileType.IDL_YAML);

      logProperties.setName(request.getNameAsString());
      logProperties.setTimestamp(timestamp);

      if (!disableVideo)
      {
         CameraSettings cameras = CameraSettingsLoader.load();

         if (target.getCameraList() != null)
         {
            for (int i = 0; i < target.getCameraList().size(); i++)
            {
               byte camera_id = target.getCameraList().get(i);

               for (CameraConfiguration camera : cameras.getCameras())
               {
                  if (camera.getCameraId() == camera_id)
                  {
                     LogTools.info("Adding camera " + camera.toString());
                     this.cameras.add(camera);
                  }
               }
            }
         }
         else
         {
            LogTools.warn("The control session has no camera's in the ControllerHosts.yaml file, so no camera's are recording... nice work genius");
         }
      }
      else if (options != null)
      {
         LogTools.warn("Video capture disabled by configuration file. Ignoring camera's and network streams");
      }
   }

   @Override
   public boolean changesVariables()
   {
      return false;
   }

   private void logHandshake(LogHandshake handshake, YoVariableHandshakeParser handshakeParser)
   {
      File handshakeFile = new File(tempDirectory, handshakeFilename);
      try
      {
         YAMLSerializer<Handshake> serializer = new YAMLSerializer<>(new HandshakePubSubType());
         serializer.serialize(handshakeFile, handshake.getHandshake());
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }

      if (handshake.getModelLoaderClass() != null)
      {
         logProperties.getModel().setLoader(handshake.getModelLoaderClass());
         logProperties.getModel().setName(handshake.getModelName());
         for (String resourceDirectory : handshake.getResourceDirectories())
         {
            logProperties.getModel().getResourceDirectoriesList().add(resourceDirectory);
         }
         logProperties.getModel().setPath(modelFilename);
         logProperties.getModel().setResourceBundle(modelResourceBundle);

         File modelFile = new File(tempDirectory, modelFilename);
         File resourceFile = new File(tempDirectory, modelResourceBundle);
         try
         {
            FileOutputStream modelStream = new FileOutputStream(modelFile, false);
            modelStream.write(handshake.getModel());
            modelStream.getFD().sync();
            modelStream.close();
            FileOutputStream resourceStream = new FileOutputStream(resourceFile, false);
            resourceStream.write(handshake.getResourceZip());
            resourceStream.getFD().sync();
            resourceStream.close();
         }
         catch (IOException e)
         {
            throw new RuntimeException(e);
         }
      }

      if (handshake.getHandshake().getSummary().getCreateSummary())
      {
         yoVariableSummarizer = new YoVariableSummarizer(handshakeParser.getYoVariablesList(),
                                                         handshake.getHandshake().getSummary().getSummaryTriggerVariableAsString(),
                                                         handshake.getHandshake().getSummary().getSummarizedVariables().toStringArray());
         logProperties.getVariables().setSummary(summaryFilename);
      }
   }

   @Override
   public void receivedTimestampAndData(long timestamp)
   {
      receivedTimestampOnly(timestamp); // Call from here as backup for the UDP channel.

      ByteBuffer buffer = reconstructBuffer(timestamp);

      connected = true;

      synchronized (synchronizer)
      {
         if (!clearingLog && dataChannel != null && dataChannel.isOpen())
         {
            if (yoVariableSummarizer != null)
               yoVariableSummarizer.setBuffer(buffer);

            if (batchTickCount == 0)
            {
               currentBatchBuffer = batchRingBuffer.next();

               // In the case where the compression thread is too slow we drop this tick
               if (currentBatchBuffer == null)
               {
                  LogTools.warn("Logger compression thread can't keep up, dropping ticks.");
                  if (yoVariableSummarizer != null)
                     yoVariableSummarizer.update();
                  updateStatus();
                  return;
               }
               currentBatchBuffer.clear();
            }

            // Buffer should have already been .clear() so its ready for reading from
            currentBatchBuffer.put(buffer);
            batchTickCount++;

            if (batchTickCount == COMPRESSION_BATCH_SIZE)
            {
               currentBatchBuffer.flip();
               batchRingBuffer.commit();
               currentBatchBuffer = null;
               batchTickCount = 0;
            }

            if (yoVariableSummarizer != null)
               yoVariableSummarizer.update();

            updateStatus();
         }
      }

   }

   private void runCompressionThread()
   {
      while (compressionThreadRunning)
      {
         writePendingBatches();
         LockSupport.parkNanos(1_000_000L); // 1ms
      }
      // Drain on exit, when the logger shuts down
      writePendingBatches();
   }

   private void writePendingBatches()
   {
      // Make sure we have a buffer to use
      if (!batchRingBuffer.poll())
         return;

      ByteBuffer batch;
      while ((batch = batchRingBuffer.read()) != null)
      {
         long batchTimestamp = batch.getLong(0);
         batch.rewind();
         compressedBuffer.clear();

         int compressedSize;
         try
         {
            compressedSize = (int) Zstd.compressByteArray(compressedBuffer.array(), 0, compressedBuffer.capacity(),
                                                          batch.array(), batch.position(), batch.remaining(),
                                                          ZSTD_COMPRESSION_LEVEL);
         }
         catch (ZstdException e)
         {
            LogTools.error("Zstd compression failed, skipping batch: " + e.getMessage());
            continue;
         }

         if (Zstd.isError(compressedSize))
         {
            LogTools.error("Zstd compression failed, skipping batch: " + Zstd.getErrorName(compressedSize));
            continue;
         }
         
         compressedBuffer.limit(compressedSize);
         compressedBuffer.position(0);

         synchronized (synchronizer)
         {
            if (!clearingLog && dataChannel != null && dataChannel.isOpen())
            {
               try
               {
                  indexBuffer.clear();
                  indexBuffer.putLong(batchTimestamp);
                  indexBuffer.putLong(dataChannel.position());
                  indexBuffer.flip();

                  indexChannel.write(indexBuffer);
                  dataChannel.write(compressedBuffer);

                  if (flushAggressivelyToDisk && ++currentIndex % FLUSH_EVERY_N_PACKETS == 0)
                  {
                     indexChannel.force(false);
                     dataChannel.force(false);
                  }
               }
               catch (IOException e)
               {
                  throw new RuntimeException(e);
               }
            }
         }
      }

      batchRingBuffer.flush();
   }

   private void updateStatus()
   {
      synchronized (synchronizer)
      {
         if (yoVariableClientInterface != null && yoVariableClientInterface.isConnected())
         {
            long now = System.nanoTime();

            if (now > lastStatusUpdateTimestamp + STATUS_PACKET_RATE)
            {
               boolean recordingVideo = false;
               for (int i = 0; i < videoDataLoggers.size(); i++)
               {
                  if (videoDataLoggers.get(i).getLastFrameReceivedTimestamp() + VIDEO_RECORDING_TIMEOUT > now)
                  {
                     recordingVideo = true;
                  }
               }

               int time = (int) MathTools.clamp(Conversions.nanosecondsToSeconds(now - logStartedTimestamp), 0, DataServerCommand.getMaximumArgumentValue());
               if (recordingVideo)
               {
                  yoVariableClientInterface.sendCommand(DataServerCommand.LOG_ACTIVE_WITH_CAMERA, time);
               }
               else
               {
                  yoVariableClientInterface.sendCommand(DataServerCommand.LOG_ACTIVE, time);
               }

               lastStatusUpdateTimestamp = now;
            }
         }
      }
   }

   /**
    * @return ByteBuffer that is prepared for reading as its position is set to zero and the limit is set to its capacity
    */
   protected ByteBuffer reconstructBuffer(long timestamp)
   {
      dataBufferAsLong.clear();

      dataBufferAsLong.put(timestamp);
      dataBufferAsLong.put(dataAsLong);
      dataBufferAsLong.put(cachedJointStateValues);

      dataBufferAsLong.flip();
      dataBuffer.clear();
      return dataBuffer;
   }

   @Override
   public void disconnected()
   {
      LogTools.info("Finalizing log from host: " + request.getHostNameAsString());

      // Commit any partial batch so the compression thread can write it
      int validTicksInLastBatch;
      synchronized (synchronizer)
      {
         validTicksInLastBatch = batchTickCount;
         if (batchTickCount > 0 && currentBatchBuffer != null)
         {
            currentBatchBuffer.flip();
            batchRingBuffer.commit();
            currentBatchBuffer = null;
            batchTickCount = 0;
         }
      }

      // Stop compression thread and wait for it to drain all pending batches
      compressionThreadRunning = false;
      LockSupport.unpark(compressionThread);
      try
      {
         compressionThread.join(5000);
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }

      try
      {
         dataChannel.close();
         indexChannel.close();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }

      for (VideoDataLoggerInterface videoDataLogger : videoDataLoggers)
      {
         closeVideo(videoDataLogger);
      }

      if (!connected)
      {
         LogTools.error("Never started logging, cleaning up from host: ", request.getHostNameAsString());
         for (VideoDataLoggerInterface videoDataLogger : videoDataLoggers)
         {
            videoDataLogger.removeLogFiles();
         }

         File handshakeFile = new File(tempDirectory, handshakeFilename);
         if (handshakeFile.exists())
         {
            LogTools.info("Deleting handshake file");
            handshakeFile.delete();
         }

         File properties = new File(tempDirectory, propertyFile);
         if (properties.exists())
         {
            LogTools.info("Deleting properties file");
            properties.delete();
         }

         File model = new File(tempDirectory, modelFilename);
         if (model.exists())
         {
            LogTools.info("Deleting model file");
            model.delete();
         }

         File resources = new File(tempDirectory, modelResourceBundle);
         {
            LogTools.info("Deleting resource bundle");
            resources.delete();
         }

         File dataFile = new File(tempDirectory, dataFilename);
         if (dataFile.exists())
         {
            LogTools.info("Deleting data file");
            dataFile.delete();
         }

         File indexFile = new File(tempDirectory, indexFilename);
         if (indexFile.exists())
         {
            LogTools.info("Deleting index file");
            indexFile.delete();
         }

         if (tempDirectory.exists())
         {
            LogTools.info("Deleting log directory");
            tempDirectory.delete();
         }
      }
      else
      {
         if (yoVariableSummarizer != null)
         {
            yoVariableSummarizer.writeData(new File(tempDirectory, summaryFilename));
         }

         logProperties.getVariables().setValidTicksInLastBatch(validTicksInLastBatch);
         try
         {
            logProperties.store();
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }

         doneListener.accept(request);

         tempDirectory.renameTo(finalDirectory);

         // This gets printed here because it's been successful and is the final location of the log directory
         LogTools.info("Log is saved as: " + finalDirectory);
      }

      if (alreadyShutDown)
      {
         LogTools.info("This may have already shutdown because the connection to the server was lost");
      }
   }

   private final ExecutorService executor = Executors.newCachedThreadPool();

   private void closeVideo(VideoDataLoggerInterface videoDataLogger)
   {
      Future<?> future = executor.submit(() -> videoDataLogger.close());
      try
      {
         future.get(5, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException e)
      {
         e.printStackTrace();
      }
      catch (TimeoutException e)
      {
         LogTools.info("Closing video stream timed out after 5s.");
      }
   }

   @Override
   public boolean updateYoVariables()
   {
      return false;
   }

   @Override
   public void setShowOverheadView(boolean showOverheadView)
   {
   }

   @SuppressWarnings("resource")
   @Override
   public void start(YoVariableClientInterface yoVariableClientInterface,
                     LogHandshake handshake,
                     YoVariableHandshakeParser handshakeParser,
                     DebugRegistry debugRegistry)
   {
      logHandshake(handshake, handshakeParser);

      int bufferSize = handshakeParser.getBufferSize();
      compressedBuffer = ByteBuffer.allocate((int) Zstd.compressBound(bufferSize * COMPRESSION_BATCH_SIZE));
      batchRingBuffer = new ConcurrentRingBuffer<>(() -> ByteBuffer.allocate(bufferSize * COMPRESSION_BATCH_SIZE), BATCH_RING_BUFFER_SIZE);

      compressionThreadRunning = true;
      compressionThread = new Thread(this::runCompressionThread, "LoggerCompressionThread");
      compressionThread.setDaemon(true);
      compressionThread.start();

      variables = handshakeParser.getYoVariablesList().toArray(new YoVariable[0]);
      jointStates = handshakeParser.getJointStates().toArray(new JointState[0]);

      // Initialize disk format variables
      dataBuffer = ByteBuffer.allocate(bufferSize);
      dataBufferAsLong = dataBuffer.asLongBuffer();

      // We can do this because once we connect to a server we don't expect the number of YoVariables to change while we are running
      dataAsLong = new long[variables.length];

      File dataFile = new File(tempDirectory, dataFilename);
      File indexFile = new File(tempDirectory, indexFilename);

      synchronized (synchronizer)
      {
         try
         {
            dataChannel = new FileOutputStream(dataFile, false).getChannel();
            indexChannel = new FileOutputStream(indexFile, false).getChannel();
         }
         catch (FileNotFoundException e)
         {
            throw new RuntimeException(e);
         }

         if (!disableVideo)
         {
            for (CameraConfiguration camera : cameras)
            {
               try
               {
                  switch (camera.getType())
                  {
                     case CAPTURE_CARD_MAGEWELL:
                        videoDataLoggers.add(new MagewellVideoDataLogger(camera.getNameAsString(),
                                                                         camera.getType().name(),
                                                                         tempDirectory,
                                                                         logProperties,
                                                                         Byte.parseByte(camera.getIdentifierAsString()),
                                                                         options));
                        break;
                     case CAPTURE_CARD:
                        videoDataLoggers.add(new BlackmagicVideoDataLogger(camera.getNameAsString(),
                                                                           camera.getType().name(),
                                                                           tempDirectory,
                                                                           logProperties,
                                                                           Byte.parseByte(camera.getIdentifierAsString()),
                                                                           options));
                        break;
                     case NETWORK_STREAM:
                        videoDataLoggers.add(new NetworkStreamVideoDataLogger(tempDirectory,
                                                                              camera.getType().name(),
                                                                              logProperties,
                                                                              LogParticipantSettings.videoDomain,
                                                                              camera.getIdentifierAsString()));
                        break;
                  }
               }
               catch (IOException e)
               {
                  LogTools.error("Cannot start video data logger");
                  e.printStackTrace();
               }
            }
         }

         // Write data to file, force it to exist
         try
         {
            logProperties.store();

            dataChannel.force(true);
            indexChannel.force(true);
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }

         this.yoVariableClientInterface = yoVariableClientInterface;

         logStartedTimestamp = System.nanoTime();
      }
   }

   @Override
   public void receivedTimestampOnly(long timestamp)
   {
      synchronized (timestampUpdater)
      {
         // We haven't received any new timestamps, shutdown the logger gracefully
         if (!alreadyShutDown && ticksWithoutNewTimestamp == TICKS_WITHOUT_DATA_BEFORE_SHUTDOWN)
         {
            LogTools.warn("Whoa whoa whoa, haven't received new timestamps in a while, maybe the server crashed without proper shutdown, stopping the logger...");
            disconnected();
            alreadyShutDown = true;
         }

         if (timestamp > lastReceivedTimestamp) // Check if this a newer timestamp. UDP is out of order and the TCP packets also call this function
         {
            for (int i = 0; i < videoDataLoggers.size(); i++)
            {
               videoDataLoggers.get(i).timestampChanged(timestamp);
            }
            lastReceivedTimestamp = timestamp;
            ticksWithoutNewTimestamp = 0;
         }
         else
         {
            ticksWithoutNewTimestamp++;
         }
      }
   }

   private void clearLog()
   {
      // Discard any partial batch and stop accepting new ones
      synchronized (synchronizer)
      {
         clearingLog = true;
         batchTickCount = 0;
         currentBatchBuffer = null;
      }

      // Stop compression thread so it can't race with the truncate
      compressionThreadRunning = false;
      LockSupport.unpark(compressionThread);
      try
      {
         compressionThread.join(2000);
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }

      // Drain and discard any pending batches in the ring (they are pre-clear data)
      if (batchRingBuffer.poll())
      {
         while (batchRingBuffer.read() != null) { }
         batchRingBuffer.flush();
      }

      try
      {
         LogTools.info("Clearing log.");
         dataChannel.truncate(0);
         indexChannel.truncate(0);
         for (VideoDataLoggerInterface videoDataLogger : videoDataLoggers)
         {
            videoDataLogger.restart();
         }
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }

      if (yoVariableSummarizer != null)
      {
         yoVariableSummarizer.restart();
      }

      // Restart compression thread for new data
      synchronized (synchronizer)
      {
         clearingLog = false;
         logStartedTimestamp = System.nanoTime();
      }
      compressionThreadRunning = true;
      compressionThread = new Thread(this::runCompressionThread, "LoggerCompressionThread");
      compressionThread.setDaemon(true);
      compressionThread.start();
   }

   @Override
   public void connected()
   {
      // Cached data so that when we receive a timestamp we already have the data ready to go
      dataAsLong = yoVariableClientInterface.getCachedVariableValues();
      cachedJointStateValues = yoVariableClientInterface.getCachedJointStateValues();
   }

   @Override
   public void receivedCommand(DataServerCommand command, int argument)
   {
      if (command == DataServerCommand.CLEAR_LOG)
      {
         clearLog();
      }
      else if (command == DataServerCommand.RESTART_LOG)
      {
         if (yoVariableClientInterface.isConnected())
         {

            LogTools.info("Restarting Log: " + request.getNameAsString());
            yoVariableClientInterface.stop();
         }
      }
   }

   private static String toString(Announcement announcement)
   {
      StringBuilder builder = new StringBuilder();

      builder.append("Announcement {");
      builder.append("\n  identifier = ");
      builder.append(announcement.identifier_);
      builder.append("\n  name = ");
      builder.append(announcement.name_);
      builder.append("\n  hostName = ");
      builder.append(announcement.hostName_);
      builder.append("\n  log = ");
      builder.append(announcement.log_);
      builder.append("\n}");

      return builder.toString();
   }

   public long getLastReceivedTimestamp()
   {
      return lastReceivedTimestamp;
   }

   public static String propertyFileNameBuilder(int childNumber)
   {
      if (childNumber < 1)
      {
         return "robotData.log";
      }
      return "robotData" + childNumber + ".log";
   }

}
