package us.ihmc.robotDataLogger.dataBuffers;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.List;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistryDecompressor
{
   private final YoVariable[] variables;
   private final List<JointState> jointStates;

   private final ByteBuffer decompressBuffer;
   private final CompressionImplementation compressionImplementation;

   private Object variableSynchronizer = null;

   public RegistryDecompressor(List<YoVariable> variables, List<JointState> jointStates)
   {
      this.variables = variables.toArray(new YoVariable[0]);
      this.jointStates = jointStates;
      this.decompressBuffer = ByteBuffer.allocate(variables.size() * 8);

      this.compressionImplementation = CompressionImplementationFactory.instance();

   }

   public void decompressSegment(RegistryReceiveBuffer buffer, int registryOffset)
   {
      decompressBuffer.clear();
      try
      {
         compressionImplementation.decompress(buffer.getData(), decompressBuffer, buffer.getNumberOfVariables() * 8);
      }
      catch (Throwable e)
      {
         // Malformed packet. Just skip.
         LogTools.error("Cannot decompress incoming packet. Skipping packet. " + e.getMessage());
         return;
      }
      decompressBuffer.flip();
      LongBuffer longData = decompressBuffer.asLongBuffer();

      // Sanity check
      if (longData.remaining() != buffer.getNumberOfVariables())
      {
         LogTools.error("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }
      int numberOfVariables = buffer.getNumberOfVariables();

      if (variableSynchronizer != null)
      {
         synchronized (variableSynchronizer)
         {
            updateVariables(buffer, registryOffset, longData, numberOfVariables);
         }
      }
      else
      {
         updateVariables(buffer, registryOffset, longData, numberOfVariables);
      }
   }

   void updateVariables(RegistryReceiveBuffer buffer, int registryOffset, LongBuffer longData, int numberOfVariables)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         variables[i + registryOffset].setValueFromLongBits(longData.get(), false);
      }

      double[] jointStateArray = buffer.getJointStates();
      if (jointStateArray.length > 0)
      {
         DoubleBuffer jointStateBuffer = DoubleBuffer.wrap(jointStateArray);
         for (int i = 0; i < jointStates.size(); i++)
         {
            jointStates.get(i).update(jointStateBuffer);
         }
      }
   }

   public void setVariableSynchronizer(Object variableSynchronizer)
   {
      this.variableSynchronizer = variableSynchronizer;
   }
}
