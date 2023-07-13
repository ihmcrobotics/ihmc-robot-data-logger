package us.ihmc.robotDataLogger.websocket.command;

import org.junit.jupiter.api.*;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.YoVariableClient;
import us.ihmc.robotDataLogger.YoVariableClientInterface;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.YoVariablesUpdatedListener;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerConnection;
import us.ihmc.yoVariables.registry.YoRegistry;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("robot-data-logger-2")
public class ClientImplementationTest
{
   private static final double dt = 0.001;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   public YoVariableServer yoVariableServer;
   public YoVariableClient yoVariableClient;
   public YoVariableClient yoVariableClientShouldFail;
   private final YoRegistry serverRegistry = new YoRegistry("Main");
   private final YoRegistry clientListenerRegistry = new YoRegistry("ListenerRegistry");
   private final ClientUpdatedListener clientListener = new ClientUpdatedListener(clientListenerRegistry);

   @BeforeEach
   public void setupServer()
   {
      // Sets the main registry for the server, and adds a JVMStatisticsGenerator to the server
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
   }

   @AfterEach
   public void shutdownServer()
   {
      //Need to stop server otherwise next test will fail when trying to start server
      yoVariableServer.close();
      LogTools.info("Shutting down server successfully");
   }

   @Test
   public void testClientBadHostException()
   {
      // Adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client
      yoVariableClient = new YoVariableClient(clientListener);

      for (int i = 0; i < 3; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () -> yoVariableClient.start("1.1.1.1.1.1", 9009));
         assertEquals("java.io.IOException: java.util.concurrent.ExecutionException: java.io.IOException: Connection refused", thrown.getMessage());
      }
   }

   @Test
   public void testClientBadConnectionException() throws IOException
   {
      // Adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client
      yoVariableClient = new YoVariableClient(clientListener);

      for (int i = 0; i < 2; i++)
      {
         HTTPDataServerConnection connection = HTTPDataServerConnection.connect("localhost", 8008);
         // Setting this to true will try to connect a model file which is good to test because this should fail
         connection.getAnnouncement().getModelFileDescription().setHasModel(true);

         Throwable thrown = assertThrows(IOException.class, () -> yoVariableClient.start(25000, connection));
         assertEquals("java.util.concurrent.ExecutionException: java.io.IOException: Invalid response received 404 Not Found", thrown.getMessage());

         yoVariableClient.stop();
      }
   }

   @Disabled
   @Test
   // This test requires manual input in order for the client to connect with the server, so when running on Bamboo it should be disabled
   // In order for this test to work correctly the user must select the same server for both clients, this is testing the failure conditions
   public void testDuplicateClientException()
   {
      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client, starts both clients, and connects the first one to the localhost
      yoVariableClient = new YoVariableClient(clientListener);
      yoVariableClientShouldFail = new YoVariableClient(clientListener);
      yoVariableClient.startWithHostSelector();

      Throwable thrown = assertThrows(RuntimeException.class, () -> yoVariableClientShouldFail.startWithHostSelector());
      assertEquals("Name collision for new child: testservercontainer. Parent name space = ListenerRegistry", thrown.getMessage());

      thrown = assertThrows(RuntimeException.class, () -> yoVariableClientShouldFail.startWithHostSelector());
      assertEquals("Client already started", thrown.getMessage());

      // These are both useful when multiple tests are going to be run because multiple servers will try to connect to the same address and throw a bug
      yoVariableClient.stop();
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
