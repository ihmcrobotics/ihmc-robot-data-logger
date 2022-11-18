package us.ihmc.robotDataLogger.websocket.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.yoVariables.registry.YoRegistry;

public class ServerImplementationTests
{
   private static final double dt = 0.001;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   public YoVariableServer yoVariableServer;
   private final YoRegistry serverRegistry = new YoRegistry("Main");
   private final YoRegistry otherRegistry = new YoRegistry("OtherRegistry");

   @Test
   public void testStartServerConditions()
   {
      boolean failure = false;

      // Creates the server and adds the main registry to the server with all the YoVariables
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      yoVariableServer.setMainRegistry(serverRegistry, null);
      yoVariableServer.start();

      // This test is intentionally start the server after its already been started, checking if the exception  will trigger
      for (int i = 0; i < 6; i++)
      {
         try
         {
            yoVariableServer.start();
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }

      failure = false;
      yoVariableServer.close();

      // Does a similar thing to the loop above but checks to make sure the exception for a stopped server will trigger
      for (int i = 0; i < 6; i++)
      {
         try
         {
            yoVariableServer.start();
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }
   }

   @Test
   public void testMainRegistryExceptions()
   {
      boolean failure = false;

      // Creates the server
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);

      // Tries to add a registry to a server that doesn't have a main registry yet, this is designed to fail
      for (int i = 0; i < 4; i++)
      {
         try
         {
            yoVariableServer.addRegistry(otherRegistry, null);
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }

      failure = false;
      yoVariableServer.setMainRegistry(serverRegistry, null);

      // Tries to set a main registry for a server that already has a main registry, this is designed to fail
      for (int i = 0; i < 4; i++)
      {
         try
         {
            yoVariableServer.setMainRegistry(serverRegistry, null);
         } catch (Exception e)
         {
            failure = true;
         }

         Assertions.assertTrue(failure);
      }
   }
}
