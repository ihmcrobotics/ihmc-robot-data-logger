package us.ihmc.robotDataLogger.dataBuffers;

import org.junit.jupiter.api.Test;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RegistryDecompressorTest
{
   @Test
   public void testUpdateVariablesPerformance()
   {
      Random random = new Random(123456L);

      // Range of YoVariables
      int minVariables = 24000;
      int maxVariables = 52000;
      int increment = 4000;

      int numberOfJoints = 2000;

      // Pre-allocate maximum size YoVariables
      List<YoVariable> yoVariables = new ArrayList<>();
      for (int i = 0; i < maxVariables; i++)
      {
         YoLong yoLong = new YoLong("var" + i, null);
         yoLong.set(random.nextLong());
         yoVariables.add(yoLong);
      }

      // Pre-allocate joint states
      List<JointState> jointStates = new ArrayList<>();
      for (int i = 0; i < numberOfJoints; i++)
      {
         jointStates.add(new OneDoFState("joint" + i));
      }

      // Create decompressor
      RegistryDecompressor decompressor = new RegistryDecompressor(yoVariables, jointStates);

      // Pre-allocate maximum long array and buffer
      long[] dataArray = new long[maxVariables];
      ByteBuffer byteBuffer = ByteBuffer.allocate(maxVariables * Long.BYTES);

      // Pre-allocate joint states buffer (position + velocity)
      double[] jointArray = new double[numberOfJoints * 2];
      for (int i = 0; i < jointArray.length; i++)
         jointArray[i] = random.nextDouble();

      // Mock buffer that just returns our fake data
      RegistryReceiveBuffer buffer = new RegistryReceiveBuffer(0);
      buffer.setJointStates(jointArray);

      // Loop over variable counts
      for (int numberOfVariables = minVariables; numberOfVariables <= maxVariables; numberOfVariables += increment)
      {
         // Fill long array with random values
         for (int i = 0; i < numberOfVariables; i++)
            dataArray[i] = random.nextLong();
         byteBuffer.rewind();

         long minTimeNs = Long.MAX_VALUE;

         for (int iter = 0; iter < 1000; iter++)
         {
            // Randomize values each iteration
            for (int i = 0; i < numberOfVariables; i++)
               dataArray[i] = random.nextLong();
            byteBuffer.rewind();

            long start = System.nanoTime();
            decompressor.updateVariables(buffer, 0, byteBuffer, numberOfVariables);
            long end = System.nanoTime();
            minTimeNs = Math.min(minTimeNs, end - start);
         }

         LogTools.info("Min time to updateVariables with " + numberOfVariables + " variables and "
                       + numberOfJoints + " joints: " + (minTimeNs / 1000.0) + " microseconds");
      }
   }
}