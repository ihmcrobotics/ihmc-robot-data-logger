package us.ihmc.robotDataLogger;

import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.yoVariables.registry.YoRegistry;

public class ServerClientConnectionTest
{
   private static final double dt = 0.001;

   private final YoVariableServer yoVariableServer;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   private final YoRegistry registry = new YoRegistry(getClass().getSimpleName());


   public ServerClientConnectionTest()
   {
      // Create server
      yoVariableServer = new YoVariableServer(getClass(), null, logSettings, dt);
      // Add main registry to server
      yoVariableServer.setMainRegistry(registry, null);
   }
}
