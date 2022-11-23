package us.ihmc.robotDataLogger.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.HandshakeFileType;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class YoVariableHandShakeBuilderTest
{
   private static final int MAX_DEPTH = 5;

   @Test
   public void testHandshake()
   {
      Random random = new Random(12451528L);
      YoRegistry root = new YoRegistry("root");
      YoVariableHandShakeBuilder handShakeBuilder = new YoVariableHandShakeBuilder(root.getName(), 0.001);

      YoRegistry[] registries = new YoRegistry[5];
      for (int r = 0; r < registries.length; r++)
      {
         registries[r] = new YoRegistry("main_" + r);
         root.addChild(registries[r]);
         generateRegistries(1, random, registries[r]);

         RegistrySendBufferBuilder builder = new RegistrySendBufferBuilder(registries[r], null);
         handShakeBuilder.addRegistryBuffer(builder);

      }
      Handshake handshake = handShakeBuilder.getHandShake();

      IDLYoVariableHandshakeParser parser = new IDLYoVariableHandshakeParser(HandshakeFileType.IDL_YAML);
      parser.parseFrom(handshake);

      List<YoRegistry> parsedRegistries = parser.getRootRegistry().getChildren();
      assertEquals(registries.length, parsedRegistries.size());
      for (int i = 0; i < parsedRegistries.size(); i++)
      {
         YoRegistry original = registries[i];
         YoRegistry parsed = parsedRegistries.get(i);
         assertEquals(original, parsed, "Registries are not equal");
      }
   }

   private void generateRegistries(int depth, Random random, YoRegistry parent)
   {
      int numberOfChildren = random.nextInt(10);

      for (int c = 0; c < numberOfChildren; c++)
      {
         int numberOfVariables = random.nextInt(50);

         YoRegistry registry = new YoRegistry(parent.getName() + "_" + c);
         for (int i = 0; i < numberOfVariables; i++)
         {
            new YoDouble(registry.getName() + "_" + i, registry);
         }
         parent.addChild(registry);

         if (depth < random.nextInt(MAX_DEPTH))
         {
            generateRegistries(depth + 1, random, registry);
         }
      }
   }
}
