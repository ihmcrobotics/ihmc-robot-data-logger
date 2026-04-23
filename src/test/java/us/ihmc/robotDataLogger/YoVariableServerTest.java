package us.ihmc.robotDataLogger;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

import java.util.ArrayList;
import java.util.List;

public class YoVariableServerTest
{
   private static final double DT = 0.001;
   private static final int NUMBER_OF_REGISTRIES = 500;
   private static final int VARIABLES_PER_REGISTRY = 60;

   @Test
   public void testUpdateSpeed()
   {
      DataServerSettings settings = new DataServerSettings(false);
      YoVariableServer server = new YoVariableServer("SpeedTest", null, settings, DT);

      YoRegistry mainRegistry = new YoRegistry("Main");
      for (int v = 0; v < VARIABLES_PER_REGISTRY; v++)
         new YoDouble("mainVar" + v, mainRegistry);
      server.setMainRegistry(mainRegistry);

      List<YoRegistry> additionalRegistries = new ArrayList<>();
      for (int r = 0; r < NUMBER_OF_REGISTRIES - 1; r++)
      {
         YoRegistry registry = new YoRegistry("Registry" + r);
         for (int v = 0; v < VARIABLES_PER_REGISTRY; v++)
            new YoDouble("var" + v, registry);
         server.addRegistry(registry, null);
         additionalRegistries.add(registry);
      }

      server.start();

      // JIT warmup
      for (int i = 0; i < 2000; i++)
      {
         long timestamp = i;
         server.update(timestamp);
         for (YoRegistry registry : additionalRegistries)
            server.update(timestamp, registry);
      }

      int iterations = 10000;
      long minTime = Long.MAX_VALUE;
      long maxTime = Long.MIN_VALUE;
      long totalTime = 0;

      for (int i = 0; i < iterations; i++)
      {
         long timestamp = 1000 + i;
         long start = System.nanoTime();
         server.update(timestamp);
         for (YoRegistry registry : additionalRegistries)
            server.update(timestamp, registry);
         long elapsed = System.nanoTime() - start;

         minTime = Math.min(minTime, elapsed);
         maxTime = Math.max(maxTime, elapsed);
         totalTime += elapsed;
      }

      LogTools.info("YoVariableServer.update() across " + NUMBER_OF_REGISTRIES + " registries x " + VARIABLES_PER_REGISTRY + " vars" + "  Min: "
                    + Conversions.nanosecondsToMicroseconds(minTime) + "us  Avg: " + Conversions.nanosecondsToMicroseconds(totalTime / iterations) + "us  Max: "
                    + Conversions.nanosecondsToMicroseconds(maxTime) + "us");

      server.close();
   }
}