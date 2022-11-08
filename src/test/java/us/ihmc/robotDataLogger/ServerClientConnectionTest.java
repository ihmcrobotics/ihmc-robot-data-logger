package us.ihmc.robotDataLogger;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.example.ExampleServer;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerClientConnectionTest
{
   private static final double dt = 0.001;
   private static final int variablesPerType = 24;
   public YoVariableServer yoVariableServer;
   private static final DataServerSettings logSettings = new DataServerSettings(true);
   private final YoRegistry registry = new YoRegistry("Main");
   private final YoRegistry listenerRegistry = new YoRegistry("ListenerRegistry");
   private final List<YoVariable> mainChangingVariables = new ArrayList<>();
   private final Random random = new Random(666);
   private long timestamp = 0;


   @Test
   public void connectToServerTest()
   {
      // Create variables for the registry
      createVariables("Main", variablesPerType, registry, mainChangingVariables);

      // Create server
      yoVariableServer = new YoVariableServer("TestServer", null, logSettings, dt);
      // Add main registry to server
      yoVariableServer.setMainRegistry(registry, null);
      // Start the server before the loop and after all registries are added to the server.
      yoVariableServer.start();
      LogTools.info("Server has started.");

      // Somehow this starts a client but currently can't recall how it gets past client.start()? Need to figure that out
      //start client
      ClientUpdatedListener clientListener = new ClientUpdatedListener(listenerRegistry);

      final YoVariableClient client = new YoVariableClient(clientListener);
      client.startWithHostSelector();
      LogTools.info("Client has started.");

      LogTools.info("Starting to loop - not sure what for though currently");

      updateVariables(mainChangingVariables);

      List<YoVariable> fromServer = clientListener.getConnectedClientVariables();

      for (int i = 0; i < clientListener.getConnectedClientVariables().size(); i++)
      {
         System.out.println(fromServer.get(i));
      }

      while (true)
      {
         // Increase timestamp and update variables
         timestamp += Conversions.secondsToNanoseconds(dt);

         // Adjust timestamp by +- 0.25 * dt to simulate jitter
         long dtFactor = Conversions.secondsToNanoseconds(dt) / 2;
         long jitteryTimestamp = timestamp + (long) ((random.nextDouble() - 0.5) * dtFactor);

         // Send main registry
         yoVariableServer.update(jitteryTimestamp);

         // Wait to not crash the network
         ThreadTools.sleepSeconds(dt);
      }
   }

   public void createVariables(String prefix, int variablesPerType, YoRegistry registry, List<YoVariable> allChangingVariables)
   {
      for (int i = 0; i < variablesPerType; i++)
      {
         new YoBoolean(prefix + "Boolean" + i, registry);
         new YoDouble(prefix + "Double" + i, registry);
         new YoInteger(prefix + "Integer" + i, registry);
         new YoLong(prefix + "Long" + i, registry);
         new YoEnum<>(prefix + "Enum" + i, registry, ExampleServer.SomeEnum.class, random.nextBoolean());
      }

      allChangingVariables.addAll(registry.collectSubtreeVariables());

      YoDouble input = new YoDouble(prefix + "Input", registry);
      YoDouble output = new YoDouble(prefix + "Output", registry);
      input.addListener((v) -> output.set(input.getValue()));
   }

   private void updateVariables(List<YoVariable> allChangingVariables)
   {
      for (int varIdx = 0; varIdx < allChangingVariables.size(); varIdx++)
      {
         updateVariable(allChangingVariables.get(varIdx));
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
}
