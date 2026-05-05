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
   private final long[] cachedVariableValues;
   private final long[] cachedJointStateValues;

   private final ByteBuffer decompressBuffer;
   private final LongBuffer decompressLongBuffer;
   private final double[] jointStateData;
   private final DoubleBuffer jointStateDoubleBuffer;

   private final CompressionImplementation compressionImplementation;

   private Object variableSynchronizer = null;

   public RegistryDecompressor(List<YoVariable> variables, List<JointState> jointStates)
   {
      this.variables = variables.toArray(new YoVariable[0]);
      this.jointStates = jointStates;
      this.cachedVariableValues = new long[variables.size()];

      // Each joint state contains more then one variable
      int totalJointStateVariables = 0;
      for (JointState jointState : jointStates)
      {
         totalJointStateVariables += jointState.getNumberOfStateVariables();
      }
      this.cachedJointStateValues = new long[totalJointStateVariables];

      this.decompressBuffer = ByteBuffer.allocate(variables.size() * 8);
      this.decompressLongBuffer = decompressBuffer.asLongBuffer();
      this.jointStateData = new double[totalJointStateVariables];
      this.jointStateDoubleBuffer = DoubleBuffer.wrap(jointStateData);
      this.compressionImplementation = CompressionImplementationFactory.instance();
   }

   public void decompressSegment(RegistryReceiveBuffer buffer, int registryOffset)
   {
      decompressBuffer.clear();
      int expectedBytes = buffer.getNumberOfVariables() * 8;
      try
      {
         compressionImplementation.decompress(buffer.getData(), decompressBuffer, expectedBytes);
      }
      catch (Throwable e)
      {
         // Malformed packet. Just skip.
         LogTools.error("Cannot decompress incoming packet. Skipping packet. " + e.getMessage());
         return;
      }
      decompressBuffer.flip();
      decompressLongBuffer.clear();
      // LongBuffer is a shared view and doesn't track ByteBuffer's limit automatically after flip()
      decompressLongBuffer.limit(decompressBuffer.limit() / 8);
      if (decompressLongBuffer.remaining() != buffer.getNumberOfVariables())
      {
         LogTools.error("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }

      // Sanity check
      if (decompressBuffer.remaining() != expectedBytes)
      {
         LogTools.error("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }
      int numberOfVariables = buffer.getNumberOfVariables();

      if (variableSynchronizer != null)
      {
         synchronized (variableSynchronizer)
         {
            updateVariables(buffer, registryOffset, decompressLongBuffer, numberOfVariables);
         }
      }
      else
      {
         updateVariables(buffer, registryOffset, decompressLongBuffer, numberOfVariables);
      }
   }

   void updateVariables(RegistryReceiveBuffer buffer, int registryOffset, LongBuffer longData, int numberOfVariables)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         long value = longData.get();
         variables[i + registryOffset].setValueFromLongBits(value, false);
         cachedVariableValues[i + registryOffset] = value;
      }

      double[] jointStateArray = buffer.getJointStates();
      if (jointStateArray.length > 0)
      {
         System.arraycopy(jointStateArray, 0, jointStateData, 0, jointStateArray.length);
         jointStateDoubleBuffer.rewind();
         for (int i = 0; i < jointStates.size(); i++)
         {
            jointStates.get(i).update(jointStateDoubleBuffer);
         }
         for (int i = 0; i < cachedJointStateValues.length; i++)
         {
            cachedJointStateValues[i] = Double.doubleToLongBits(jointStateData[i]);
         }
      }
   }

   public long[] getCachedVariableValues()
   {
      return cachedVariableValues;
   }

   public long[] getCachedJointStateValues()
   {
      return cachedJointStateValues;
   }

   public void setVariableSynchronizer(Object variableSynchronizer)
   {
      this.variableSynchronizer = variableSynchronizer;
   }
}
