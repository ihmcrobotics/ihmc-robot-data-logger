package us.ihmc.robotDataLogger.dataBuffers;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class RegistryReceiveBuffer extends RegistryBuffer
{
   private long receivedTimestamp;
   private ByteBuffer compressedVariableDataBuffer;
   private double[] jointStates;

   public RegistryReceiveBuffer(long receivedTimestamp)
   {
      this.receivedTimestamp = receivedTimestamp;
   }

   public void setReceivedTimestamp(long receivedTimestamp)
   {
      this.receivedTimestamp = receivedTimestamp;
   }

   public long getReceivedTimestamp()
   {
      return receivedTimestamp;
   }

   public ByteBuffer allocateBuffer(int size)
   {
      if (compressedVariableDataBuffer == null || compressedVariableDataBuffer.capacity() < size)
         compressedVariableDataBuffer = ByteBuffer.allocate(size);
      compressedVariableDataBuffer.clear();
      return compressedVariableDataBuffer;
   }

   public double[] allocateStates(int stateLength)
   {
      if (jointStates == null || jointStates.length < stateLength)
         jointStates = new double[stateLength];
      return jointStates;
   }

   public double[] getJointStates()
   {
      return jointStates;
   }

   void setJointStates(double[] jointStates)
   {
      this.jointStates = jointStates;
   }

   public ByteBuffer getData()
   {
      return compressedVariableDataBuffer;
   }

   @Override
   public String toString()
   {
      return "RegistryReceiveBuffer [receivedTimestamp=" + receivedTimestamp + ", compressedVariableDataBuffer=" + compressedVariableDataBuffer
            + ", registryID=" + registryID + ", jointStates=" + Arrays.toString(jointStates) + ", timestamp=" + timestamp + ", uid=" + uid + "]";
   }

}
