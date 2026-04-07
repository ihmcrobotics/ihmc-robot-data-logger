package us.ihmc.robotDataLogger.dataBuffers;

import java.nio.ByteBuffer;
import java.util.List;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistryDecompressor
{
   private final long[] cachedVariableValues;
   private final long[] cachedJointStateValues;

   private final ByteBuffer decompressBuffer;
   private final CompressionImplementation compressionImplementation;

   private Object variableSynchronizer = null;

   public RegistryDecompressor(List<YoVariable> variables, List<JointState> jointStates)
   {
      this.cachedVariableValues = new long[variables.size()];

      // Each joint state contains more then one variable
      int totalJointStateVariables = 0;
      for (JointState jointState : jointStates)
      {
         totalJointStateVariables += jointState.getNumberOfStateVariables();
      }
      this.cachedJointStateValues = new long[totalJointStateVariables];

      this.decompressBuffer = ByteBuffer.allocate(variables.size() * 8);
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
            updateVariables(buffer, registryOffset, decompressBuffer, numberOfVariables);
         }
      }
      else
      {
         updateVariables(buffer, registryOffset, decompressBuffer, numberOfVariables);
      }
   }

   void updateVariables(RegistryReceiveBuffer buffer, int registryOffset, ByteBuffer byteData, int numberOfVariables)
   {
      for (int i = 0; i < numberOfVariables; i++)
      {
         cachedVariableValues[i + registryOffset] = byteData.getLong();
      }

      double[] jointStateArray = buffer.getJointStates();
      if (jointStateArray.length > 0)
      {
         for (int i = 0; i < cachedJointStateValues.length; i++)
         {
            cachedJointStateValues[i] = Double.doubleToLongBits(jointStateArray[i]);
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
