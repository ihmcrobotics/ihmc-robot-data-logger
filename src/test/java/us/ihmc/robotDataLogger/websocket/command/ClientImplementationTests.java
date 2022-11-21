package us.ihmc.robotDataLogger.websocket.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.thread.ThreadTools;
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

public class ClientImplementationTests
{
   private static final double dt = 0.001;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   public YoVariableServer yoVariableServer;
   public YoVariableClient yoVariableClient;
   public YoVariableClient yoVariableClientShouldFail;
   private final YoRegistry serverRegistry = new YoRegistry("Main");
   private final YoRegistry clientListenerRegistry = new YoRegistry("ListenerRegistry");
   private final ClientUpdatedListener clientListener = new ClientUpdatedListener(clientListenerRegistry);

   @Test
   public void testClientBadHostException()
   {
      boolean failure = false;

      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();


      // Creates the client and adds the listener to the client
      yoVariableClient = new YoVariableClient(clientListener);

      for (int i = 0; i < 4; i++)
      {
         try
         {
            yoVariableClient.start("1.1.1.1.1.1.1", 9009);
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }

      LogTools.info("Closing down server and client successfully");

      // These are both useful when multiple tests are going to be run because multiple servers will try to connect to the same address and throw a bug
      yoVariableServer.close();
   }

   @Disabled
   @Test
   public void testClientBadConnectionException()
   {
      boolean failure;

      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();


      // Creates the client and adds the listener to the client
      yoVariableClient = new YoVariableClient(clientListener);

      for (int i = 0; i < 4; i++)
      {
         failure = startWithSetModelTrue("localhost", 8008, yoVariableClient);

         yoVariableClient.stop();

         Assertions.assertTrue(failure);
      }

      LogTools.info("Closing down server and client successfully, test passed");

      // This is useful when multiple tests are going to be run because multiple servers will try to connect to the same address and throw a bug
      yoVariableServer.close();
   }

   // The purpose of this function is to set the connection announcement model to true, this will run different parts of the code in the test
   public boolean startWithSetModelTrue(String host, int port, YoVariableClient yoVariableClient)
   {
      try
      {
         HTTPDataServerConnection connection = HTTPDataServerConnection.connect(host, port);

         // Setting this to true will try to connect a model file which is good to test because this should fail
         connection.getAnnouncement().getModelFileDescription().setHasModel(true);
         yoVariableClient.start(25000, connection);
      }
      catch (IOException e)
      {
         return true;
      }

      return false;
   }



   @Disabled
   @Test
   // This test requires manual input in order for the client to connect with the server, so when running on Bamboo it should be disabled
   // In order for this test to work correctly the user must select the same server for both clients, this is testing the failure conditions
   public void testDuplicateClientException()
   {
      boolean failure = false;

      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client, starts both clients, and connects the first one to the localhost
      yoVariableClient = new YoVariableClient(clientListener);
      yoVariableClientShouldFail = new YoVariableClient(clientListener);
      yoVariableClient.startWithHostSelector();

      for (int i = 0; i < 2; i++)
      {
         try
         {
            // This client should fail if the user connects it to the same port as the other client, checks to make sure the failure conditions hold
            yoVariableClientShouldFail.startWithHostSelector();
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }

      LogTools.info("Closing down server and client successfully");

      // These are both useful when multiple tests are going to be run because multiple servers will try to connect to the same address and throw a bug
      yoVariableClient.stop();
      yoVariableServer.close();
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
