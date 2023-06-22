package us.ihmc.robotDataLogger.websocket.command;

import org.junit.jupiter.api.*;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("robot-data-logger-2")
public class ServerImplementationTest
{
   private static final double dt = 0.001;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   public YoVariableServer yoVariableServer;
   private final YoRegistry serverRegistry = new YoRegistry("Main");
   private final YoRegistry otherRegistry = new YoRegistry("OtherRegistry");

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
   }

   @Test
   public void testYoVariableConnections()
   {
      yoVariableServer.setMainRegistry(serverRegistry, null);

      //Creates a summary tests adding real and fake variables to the summary
      yoVariableServer.createSummary(new YoDouble("YoDoubleSummarize", serverRegistry));
      yoVariableServer.addSummarizedVariable("Main.YoDoubleSummarize");

      for (int i = 0; i < 4; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
            yoVariableServer.addSummarizedVariable("BadVariableInformation"));

         assertEquals("Variable BadVariableInformation is not registered with the logger", thrown.getMessage());
      }
   }


   @Test
   public void testRegistryHolderException()
   {
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // This test is intentionally start the server after its already been started, checking if the exception  will trigger
      for (int i = 0; i < 6; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
                 yoVariableServer.getRegistryHolder(otherRegistry));

         assertEquals("Registry OtherRegistry not registered with addRegistry() or setMainRegistry()", thrown.getMessage());
      }
   }


   @Test
   public void testStartServerConditions()
   {
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // This test is intentionally start the server after its already been started, checking if the exception  will trigger
      for (int i = 0; i < 6; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
                 yoVariableServer.start());

         assertEquals("Server already started", thrown.getMessage());
      }

      // Close the server here to test different exception
      yoVariableServer.close();

      // Does a similar thing to the loop above but checks to make sure the exception for a stopped server will trigger
      for (int i = 0; i < 6; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
                 yoVariableServer.start());

         assertEquals("Server already started", thrown.getMessage());
      }
   }

   @Test
   public void testMainRegistryExceptions()
   {
      // Tries to add a registry to a server that doesn't have a main registry yet, this is designed to fail
      for (int i = 0; i < 4; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
                 yoVariableServer.addRegistry(otherRegistry, null));

         assertEquals("Main registry is not set. Set main registry first", thrown.getMessage());
      }

      yoVariableServer.setMainRegistry(serverRegistry, null);

      // Tries to set a main registry for a server that already has a main registry, this is designed to fail
      for (int i = 0; i < 4; i++)
      {
         Throwable thrown = assertThrows(RuntimeException.class, () ->
                 yoVariableServer.setMainRegistry(serverRegistry, null));

         assertEquals("Main registry is already set", thrown.getMessage());
      }
   }
}
