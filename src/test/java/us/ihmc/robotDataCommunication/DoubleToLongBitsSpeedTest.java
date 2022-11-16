package us.ihmc.robotDataCommunication;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class DoubleToLongBitsSpeedTest
{
   @Test
   public void testDoubleToLongBitsSpeed()
   {
      int numberOfVariables = 10000;
      long startTime = 0;
      double doubleTimeTaken;
      double longTimeTaken;


      Random random = new Random(1234);
      ArrayList<YoDouble> variables = new ArrayList<>(numberOfVariables);

      YoRegistry registry = new YoRegistry("TestRegistry");

      // Seems like a lot of variables and maybe it could be a bit less, NOT CURRENTLY SURE WHY THIS HAPPENS
      for (int i = 0; i < numberOfVariables; i++)
      {
         YoDouble v = new YoDouble("test-" + i, registry);
         v.set(random.nextDouble());
         variables.add(v);
      }

      // Buffers created for the test, the original buffer is just used to generate the double and long buffer
      ByteBuffer buffer = ByteBuffer.allocate(numberOfVariables * 8);
      DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();
      LongBuffer longBuffer = buffer.asLongBuffer();

      // Used to optimize the JIT Compiler, that way the time taken is accurate to the fullest extent
      for (int i = 0; i < 4200; i++)
      {
         fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
         fillLongBuffer(numberOfVariables, variables, longBuffer);
         doubleBuffer.clear();
         longBuffer.clear();
      }

      for (int i = 0; i < 120; i++)
      {
         doubleBuffer.clear();
         longBuffer.clear();

         startTime = System.nanoTime();
         fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
         doubleTimeTaken = (System.nanoTime() - startTime) / 1e6;

         startTime = System.nanoTime();
         fillLongBuffer(numberOfVariables, variables, longBuffer);
         longTimeTaken = (System.nanoTime() - startTime) / 1e6;

         Assertions.assertFalse(doubleTimeTaken > longTimeTaken,
                                "Double Buffer took: " + doubleTimeTaken + ", and Long Buffer took: " + longTimeTaken + "For loop: " + i);
      }
   }

   // Fills the DoubleBuffer with doubles from the list
   private static void fillDoubleBuffer(int numberOfVariables, ArrayList<YoDouble> variables, DoubleBuffer doubleBuffer)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         doubleBuffer.put(variables.get(i).getDoubleValue());
      }
   }

   // Fills the LongBuffer with doubles from the list, has to convert to long before it can be added
   private static void fillLongBuffer(int numberOfVariables, ArrayList<YoDouble> variables, LongBuffer longBuffer)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         longBuffer.put(Double.doubleToLongBits(variables.get(i).getDoubleValue()));
      }
   }
}
