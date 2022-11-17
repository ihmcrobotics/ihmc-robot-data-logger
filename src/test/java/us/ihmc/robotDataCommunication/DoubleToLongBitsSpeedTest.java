package us.ihmc.robotDataCommunication;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import java.util.function.Supplier;

public class DoubleToLongBitsSpeedTest
{
   int numberOfVariables = 100000;
   Random random = new Random();

   // Made use of a supplier for future updates with the test, this allows the user to grab the next bit of data each time the supplier is called
   // Currently its only called once so the use of the supplier isn't needed
   Supplier<ArrayList<YoDouble>> generateRandomData()
   {
      return () ->
      {
         YoRegistry registry = new YoRegistry("TestRegistry");
         ArrayList<YoDouble> variables = new ArrayList<>(numberOfVariables);

         for (int i = 0; i < numberOfVariables; i++)
         {
            YoDouble v = new YoDouble("test-" + i, registry);
            v.set(random.nextDouble());
            variables.add(v);
         }

         return variables;
      };
   }

   @Test
   public void testDoubleToLongBitsSpeed()
   {
      // Variables needed for testing
      double doubleTimeTaken;
      double longTimeTaken;
      Stopwatch doubleTime = new Stopwatch();
      Stopwatch longTime = new Stopwatch();
      ArrayList<YoDouble> variables;

      // Buffers created for the test, the original buffer is just used to generate the double and long buffer
      ByteBuffer buffer = ByteBuffer.allocate(numberOfVariables * 8);
      DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();
      LongBuffer longBuffer = buffer.asLongBuffer();

      //Fills the ArrayList with random values that will get tested
      variables = generateRandomData().get();

      // Used to optimize the JIT Compiler, that way the time taken is accurate to the fullest extent
      for (int i = 0; i < 4200; i++)
      {
         fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
         fillLongBuffer(numberOfVariables, variables, longBuffer);
         doubleBuffer.clear();
         longBuffer.clear();
      }

      //Runs the DoubleBuffer a bunch and stores how long it takes to run them all
      doubleTime.start();
      for (int i = 0; i < 10000; i++)
      {
         fillDoubleBuffer(numberOfVariables, variables, doubleBuffer);
         doubleBuffer.clear();
      }
      doubleTimeTaken = doubleTime.totalElapsed();

      //Runs the LongBuffer a bunch and stores how long it takes to run them all
      longTime.start();
      for (int i = 0; i < 10000; i++)
      {
         fillLongBuffer(numberOfVariables, variables, longBuffer);
         longBuffer.clear();
      }
      longTimeTaken = longTime.totalElapsed();

      // On each individual attempt the double conversion may be slower than the long conversion but over time and on average, the double
      // will be faster than the long which is how the test is designed
      Assertions.assertTrue(doubleTimeTaken < longTimeTaken,
                             "Double Buffer took: " + doubleTimeTaken + ", and Long Buffer took: " + longTimeTaken);
      Assertions.assertEquals(doubleBuffer.capacity(), longBuffer.capacity());
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
