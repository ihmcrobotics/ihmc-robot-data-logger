package us.ihmc.robotDataLogger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServerClientConnectionTest
{
   // This method is used when creating the YoEnums
   public enum SomeEnum
   {
      A, B, C, D, E, F
   }

   private static final double dt = 0.001;
   private static final int variablesPerType = 24;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   private final List<YoVariable> mainChangingVariables = new ArrayList<>();
   public YoVariableServer yoVariableServer;
   public YoVariableClient yoVariableClient;
   private final YoRegistry serverRegistry = new YoRegistry("Main");
   private final YoRegistry clientListenerRegistry = new YoRegistry("ListenerRegistry");
   private final ClientUpdatedListener clientListener = new ClientUpdatedListener(clientListenerRegistry);

   @Test
   public void testReconnectToClient() throws IOException
   {
      String receivedServerName;
      Stopwatch timer = new Stopwatch();
      timer.start();

      // This method creates all the YoVariables to be stored on the server, change how many with the variablesPerType int
      createVariables("Main", variablesPerType, serverRegistry, mainChangingVariables);

      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client, then the client is started as well
      yoVariableClient = new YoVariableClient(clientListener);
      yoVariableClient.start("localhost", 8008);

      // Message to let the user know that the client and server should now both be running
      LogTools.info("Server and Client are started!");

      while(timer.totalElapsed() < 6.0)
      {
         Assertions.assertTrue(yoVariableClient.isConnected());

         yoVariableClient.disconnect();

         // Needs to sleep for a bit to let the client shutdown properly, otherwise the test will fail
         ThreadTools.sleepSeconds(1);
         assertFalse(yoVariableClient.isConnected());

         yoVariableClient.reconnect();
         Assertions.assertTrue(yoVariableClient.isConnected());

         receivedServerName = yoVariableClient.getServerName();
         Assertions.assertEquals("TestServer", receivedServerName);
      }

      yoVariableClient.stop();
      assertFalse(yoVariableClient.isConnected());

      // Prevents bug when creating more than one server across multiple tests because the servers by default go to the same address
      yoVariableServer.close();
   }

   public void createVariables(String prefix, int variablesPerType, YoRegistry registry, List<YoVariable> allChangingVariables)
   {
      for (int i = 0; i < variablesPerType; i++)
      {
         new YoBoolean(prefix + "Boolean" + i, registry);
         new YoDouble(prefix + "Double" + i, registry);
         new YoInteger(prefix + "Integer" + i, registry);
         new YoLong(prefix + "Long" + i, registry);
         new YoEnum<>(prefix + "Enum" + i, registry, SomeEnum.class, true);
      }

      allChangingVariables.addAll(registry.collectSubtreeVariables());
   }

   /** Class that implements the YoVariableUpdatedListener to connect with the client */
   public static class ClientUpdatedListener implements YoVariablesUpdatedListener
   {
      private final YoRegistry parentRegistry;

      public ClientUpdatedListener(YoRegistry parentRegistry)
      {
         this.parentRegistry = parentRegistry;
      }

      @Override
      public boolean updateYoVariables()
      {
         return true;
      }

      @Override
      public boolean changesVariables()
      {
         return false;
      }

      @Override
      public void setShowOverheadView(boolean showOverheadView)
      {

      }

      @Override
      public void start(YoVariableClientInterface yoVariableClientInterface,
                        LogHandshake handshake,
                        YoVariableHandshakeParser handshakeParser,
                        DebugRegistry debugRegistry)
      {
         YoRegistry clientRootRegistry = handshakeParser.getRootRegistry();
         YoRegistry serverRegistry = new YoRegistry(yoVariableClientInterface.getServerName() + "Container");
         serverRegistry.addChild(clientRootRegistry);
         parentRegistry.addChild(serverRegistry);
      }

      @Override
      public void disconnected()
      {

      }

      @Override
      public void receivedTimestampAndData(long timestamp)
      {

      }

      @Override
      public void connected()
      {

      }

      @Override
      public void receivedCommand(DataServerCommand command, int argument)
      {

      }

      @Override
      public void receivedTimestampOnly(long timestamp)
      {

      }
   }
}
