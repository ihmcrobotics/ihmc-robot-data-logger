package us.ihmc.robotDataLogger;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import us.ihmc.commons.MathTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.handshake.IDLYoVariableHandshakeParser;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.interfaces.VariableChangedProducer;
import us.ihmc.robotDataLogger.util.DaemonThreadFactory;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.client.WebsocketDataConsumer;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;

/**
 * Client for the logger This is a general client for a logging sessions. A listener can be attached
 * to provide desired functionality.
 *
 * @author jesper
 */
public class YoVariableClientImplementation implements YoVariableClientInterface
{
   private String serverName;

   private final VariableChangedProducer variableChangedProducer;

   // Command executor
   private final Executor commandExecutor = Executors.newSingleThreadExecutor(DaemonThreadFactory.getNamedDaemonThreadFactory(getClass().getSimpleName()));

   // Callback
   private final YoVariablesUpdatedListener yoVariablesUpdatedListener;

   private WebsocketDataConsumer dataConsumer;

   YoVariableClientImplementation(final YoVariablesUpdatedListener yoVariablesUpdatedListener)
   {
      this.yoVariablesUpdatedListener = yoVariablesUpdatedListener;
      if (yoVariablesUpdatedListener.changesVariables())
      {
         variableChangedProducer = new VariableChangedProducer();
      }
      else
      {
         variableChangedProducer = null;
      }
   }

   @Override
   public String getServerName()
   {
      return serverName;
   }

   /**
    * Callback function from the RegistryConsumer. Gets called when the connection is closed, either by
    * timeout or by user request.
    */
   public void connectionClosed()
   {
      LogTools.info("Disconnected, closing client.");
      yoVariablesUpdatedListener.disconnected();
   }

   /**
    * Start a new session and request the handshake and robot model. This can only be called once for a
    * client. To restart, use reconnect()
    *
    * @param timeout
    * @param connection
    * @throws IOException
    */
   public synchronized void start(int timeout, HTTPDataServerConnection connection) throws IOException
   {
      if (dataConsumer != null)
      {
         throw new RuntimeException("Client already started");
      }

      Announcement announcement = connection.getAnnouncement();

      dataConsumer = new WebsocketDataConsumer(connection, timeout);
      serverName = connection.getAnnouncement().getNameAsString();

      LogTools.info("Requesting handshake, model, and resource bundle from some stuff...");
      Handshake handshake = dataConsumer.getHandshake();

      IDLYoVariableHandshakeParser handshakeParser = new IDLYoVariableHandshakeParser(HandshakeFileType.IDL_CDR);
      handshakeParser.parseFrom(handshake);

      LogHandshake logHandshake = new LogHandshake();
      logHandshake.setHandshake(handshake);
      if (announcement.getModelFileDescription().getHasModel())
      {
         String modelName = announcement.getModelFileDescription().getNameAsString();
         logHandshake.setModelName(modelName);
         // Requesting model file
         logHandshake.setModel(dataConsumer.getModelFile());
         logHandshake.setModelLoaderClass(announcement.getModelFileDescription().getModelLoaderClassAsString());
         logHandshake.setResourceDirectories(announcement.getModelFileDescription().getResourceDirectories().toStringArray());
         if (announcement.getModelFileDescription().getHasResourceZip())
         {
            // Requesting resource bundle
            logHandshake.setResourceZip(dataConsumer.getResourceZip());
         }
         LogTools.info("Received model, resource bundle, and the works");

      }

      if (variableChangedProducer != null)
      {
         variableChangedProducer.startVariableChangedProducers(handshakeParser.getYoVariablesList(), dataConsumer);
      }

      DebugRegistry debugRegistry = new DebugRegistry();
      yoVariablesUpdatedListener.start(this, logHandshake, handshakeParser, debugRegistry);
      connectToSession(handshakeParser, debugRegistry);
   }

   /**
    * Internal function to connect to a session Throws a runtimeexception if you are already connected
    * or when the client has closed to connection
    * @param handshakeParser 
    *
    * @param debugRegistry
    * @throws IOException
    */
   void connectToSession(IDLYoVariableHandshakeParser handshakeParser, DebugRegistry debugRegistry) throws IOException
   {
      if (dataConsumer.isSessionActive())
      {
         throw new RuntimeException("Client already connected");
      }
      if (dataConsumer.isClosed())
      {
         throw new RuntimeException("Client has closed completely");
      }
      dataConsumer.startSession(handshakeParser, this, variableChangedProducer, yoVariablesUpdatedListener, yoVariablesUpdatedListener, debugRegistry);
   }

   /**
    * Stops the client completely. The participant leaves the domain and a reconnect is not possible.
    */
   @Override
   public synchronized void stop()
   {
      if (dataConsumer == null)
      {
         throw new RuntimeException("Session not started");
      }
      dataConsumer.close();
      dataConsumer = null;
   }

   /**
    * Broadcast a clear log request for the current session If no session is available, this request
    * gets silently ignored.
    */
   @Override
   public void sendClearLogRequest()
   {
      try
      {
         dataConsumer.sendCommand(DataServerCommand.CLEAR_LOG, 0);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   /**
    * Callback from the timestamp topic. Gets called immediately when a new timestamp has arrived from
    * the logger.
    *
    * @param timestamp
    */
   public void receivedTimestampAndData(long timestamp)
   {
      yoVariablesUpdatedListener.receivedTimestampAndData(timestamp);
   }

   @Override
   public void disconnect()
   {
      if (dataConsumer != null)
      {
         dataConsumer.disconnectSession();
      }
   }

   @Override
   public synchronized boolean reconnect() throws IOException
   {
      if (dataConsumer == null)
      {
         throw new RuntimeException("Session not started");
      }

      return dataConsumer.reconnect();
   }

   public void receivedCommand(DataServerCommand command, int argument)
   {
      commandExecutor.execute(() -> yoVariablesUpdatedListener.receivedCommand(command, argument));
   }

   public void connected()
   {
      yoVariablesUpdatedListener.connected();
   }

   @Override
   public void setVariableUpdateRate(int updateRate)
   {
      updateRate = MathTools.clamp(updateRate, 0, 99999);

      try
      {
         dataConsumer.sendCommand(DataServerCommand.LIMIT_RATE, updateRate);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   @Override
   public boolean isConnected()
   {
      return dataConsumer != null && dataConsumer.isSessionActive();
   }

   @Override
   public void sendCommand(DataServerCommand command, int argument)
   {
      try
      {
         dataConsumer.sendCommand(command, argument);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   public void setVariableSynchronizer(Object variableSynchronizer)
   {
      dataConsumer.setVariableSynchronizer(variableSynchronizer);
   }
}
