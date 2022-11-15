package us.ihmc.robotDataCommunication;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.Test;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class DoubleToLongBitsSpeedTest
{
   @Test
   public void testDoubleToLongBitsSpeed()
   {
      int numberOfVariables = 10000;

      Random random = new Random(1234);
      ArrayList<YoDouble> variables = new ArrayList<>(numberOfVariables);

      YoRegistry registry = new YoRegistry("TestRegistry");

      // Seems like a lot of variables and maybe it could be a bit less
      for (int i = 0; i < numberOfVariables; i++)
      {
         YoDouble v = new YoDouble("test-" + i, registry);
         v.set(random.nextDouble());
         variables.add(v);
      }

      // Because this is going to be a ton of space as well, doens't seem important
      // Creats a ByteBuffer, a DoubleBuffer, and a LongBuffer
      ByteBuffer buffer = ByteBuffer.allocate(numberOfVariables * 8);
      DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();
      LongBuffer longBuffer = buffer.asLongBuffer();

      // Is this meant to optimize the git complier? Becuase is so, thats a shitty job, might as well run it 5000 times because after a 100 things are not that
      // optimized, and it its taking so long them just use less variables
      for (int i = 0; i < 100; i++)
      {
         fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
         fillLongBuffer(numberOfVariables, variables, longBuffer);
         doubleBuffer.clear();
         longBuffer.clear();
      }

      long start = System.nanoTime();
      fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
      double end = (System.nanoTime() - start) / 1e6;
      System.out.println("Double buffer took " + end + " ms");

      start = System.nanoTime();
      fillLongBuffer(numberOfVariables, variables, longBuffer);
      end = (System.nanoTime() - start) / 1e6;
      System.out.println("Long buffer took " + end + " ms");

   }

   private static void fillDoubleBuffer(int numberOfVariables, ArrayList<YoDouble> variables, DoubleBuffer doubleBuffer)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         doubleBuffer.put(variables.get(i).getDoubleValue());
      }
   }

   private static void fillLongBuffer(int numberOfVariables, ArrayList<YoDouble> variables, LongBuffer longBuffer)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         longBuffer.put(Double.doubleToLongBits(variables.get(i).getDoubleValue()));
      }
   }
}
