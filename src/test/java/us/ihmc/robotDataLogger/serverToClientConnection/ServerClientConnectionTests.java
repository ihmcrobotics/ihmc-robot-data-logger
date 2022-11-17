package us.ihmc.robotDataLogger.serverToClientConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.YoVariableClient;
import us.ihmc.robotDataLogger.YoVariableClientInterface;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.YoVariablesUpdatedListener;
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
import java.util.Random;

public class ServerClientConnectionTests
{
   // This method is used when creating the YoEnums
   public enum SomeEnum
   {
      A, B, C, D, E, F;
   }

   private static final double dt = 0.001;
   private static final int variablesPerType = 24;
   private long timestamp = 0;
   private final Random random = new Random(666);
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
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client, then the client is started as well
      yoVariableClient = new YoVariableClient(clientListener);
      yoVariableClient.start("localhost", 8008);

      // Message to let the user know that the client and server should now both be running
      LogTools.info("Server and Client are started!");

      while(timer.totalElapsed() < 12)
      {
         Assertions.assertTrue(yoVariableClient.isConnected());

         yoVariableClient.disconnect();

         // Needs to sleep for a bit to let the client shutdown properly, otherwise the test will fail
         ThreadTools.sleepSeconds(1);
         Assertions.assertFalse(yoVariableClient.isConnected());

         yoVariableClient.reconnect();
         Assertions.assertTrue(yoVariableClient.isConnected());

         receivedServerName = yoVariableClient.getServerName();
         Assertions.assertEquals("TestServer", receivedServerName);
      }

      yoVariableClient.stop();
      Assertions.assertFalse(yoVariableClient.isConnected());

      // Prevents bug when creating more than one server across multiple tests because the servers by default go to the same address
      yoVariableServer.close();
      ThreadTools.sleepSeconds(1);
   }

   @Test
   public void testSendingVariablesToClient()
   {
      // This method creates all the YoVariables to be stored on the server, change how many with the variablesPerType int
      createVariables("Main", variablesPerType, serverRegistry, mainChangingVariables);

      // Creates the server and adds the main registry to the server with all the YoVariables, the server is then started
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // Creates the client and adds the listener to the client, then the client is started as well
      yoVariableClient = new YoVariableClient(clientListener);
      yoVariableClient.start("localhost", 8008);

      // Message to let the user know that the client and server should now both be running
      LogTools.info("Server and Client are started!");

      for (int i = 0; i < 6; i++)
      {
         // timestamp and dtFactor are used to generate the jitteryTimestamp that will be sent to the server as the time when the update method was called
         timestamp += Conversions.secondsToNanoseconds(dt);
         long dtFactor = Conversions.secondsToNanoseconds(dt) / 2;
         long jitteryTimestamp = timestamp + (long) ((random.nextDouble() - 0.5) * dtFactor);

         // Update the YoVariables before sending the data to the server
            updateVariables(mainChangingVariables);

         // This should take the timestamp and send the updated variables to the client as well as the timestamp
         update(jitteryTimestamp);

         // Store the variables that are in the server and client, this will allow us to compare the values and see if they match
         List<YoVariable> serverVariables = new ArrayList<>(serverRegistry.collectSubtreeVariables());
         List<YoVariable> clientVariables = new ArrayList<>(clientListenerRegistry.collectSubtreeVariables());

         for (int j = 0; j < serverVariables.size(); j++)
         {
            Assertions.assertEquals(serverVariables.get(j).getValueAsString(), clientVariables.get(j).getValueAsString(),
                                    "The server variable: " + serverVariables.get(j) + ", the client variable: "
                                    + clientVariables.get(j));
         }
      }

      // These are both useful when multiple tests are going to be run because multiple servers will try to connect to the same address and throw a bug
      yoVariableClient.stop();
      yoVariableServer.close();
   }

   // This method is just used to clean up the main loop, basically because of time and idk what else, the update method needs to be called several times and
   // the program needs to wait for a bit for the client to detect that the variables have changed on the server, WEIRD
   public void update(long jitteryTimestamp)
   {
      yoVariableServer.update(jitteryTimestamp);
      yoVariableServer.update(jitteryTimestamp);

      ThreadTools.sleepSeconds(6);
      yoVariableServer.update(jitteryTimestamp);
      yoVariableServer.update(jitteryTimestamp);

      ThreadTools.sleepSeconds(6);
      yoVariableServer.update(jitteryTimestamp);
      yoVariableServer.update(jitteryTimestamp);

      // Since the networks don't run at the same rate, it's good to sleep for a bit in order to not crash the network
      ThreadTools.sleepSeconds(18);
      yoVariableServer.update(jitteryTimestamp);
      yoVariableServer.update(jitteryTimestamp);
   }


   public void createVariables(String prefix, int variablesPerType, YoRegistry registry, List<YoVariable> allChangingVariables)
   {
      for (int i = 0; i < variablesPerType; i++)
      {
         new YoBoolean(prefix + "Boolean" + i, registry);
         new YoDouble(prefix + "Double" + i, registry);
         new YoInteger(prefix + "Integer" + i, registry);
         new YoLong(prefix + "Long" + i, registry);
         new YoEnum<>(prefix + "Enum" + i, registry, SomeEnum.class, random.nextBoolean());
      }

      allChangingVariables.addAll(registry.collectSubtreeVariables());
   }

   private void updateVariables(List<YoVariable> allChangingVariables)
   {
      for (YoVariable allChangingVariable : allChangingVariables)
      {
         updateVariable(allChangingVariable);
      }
   }

   private void updateVariable(YoVariable variable)
   {
      if (variable instanceof YoBoolean)
      {
         ((YoBoolean) variable).set(random.nextBoolean());
      }
      else if (variable instanceof YoDouble)
      {
         ((YoDouble) variable).set(random.nextDouble());
      }
      else if (variable instanceof YoInteger)
      {
         ((YoInteger) variable).set(random.nextInt());
      }
      else if (variable instanceof YoLong)
      {
         ((YoLong) variable).set(random.nextLong());
      }
      else if (variable instanceof YoEnum<?>)
      {
         int enumSize = ((YoEnum<?>) variable).getEnumSize();
         ((YoEnum<?>) variable).set(random.nextInt(enumSize));
      }
      else
      {
         throw new RuntimeException("Implement this case for " + variable.getClass().getSimpleName() + ".");
      }
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
