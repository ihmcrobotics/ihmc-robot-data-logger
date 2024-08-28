package us.ihmc.robotDataLogger.memoryLogger;

import java.nio.ByteBuffer;

class MemoryBufferEntry
{
   final ByteBuffer variables[];
   final double[][] jointStates;
   final long[] timestamps;
   
   public MemoryBufferEntry(int numberOfBuffers)
   {
      variables = new ByteBuffer[numberOfBuffers];
      jointStates = new double[numberOfBuffers][];
      timestamps = new long[numberOfBuffers];         
   }
   
   public void initializeRegistry(int registryID, int numberOfVariables, int numberOfJointStates)
   {
      variables[registryID] = ByteBuffer.allocateDirect(numberOfVariables * Long.BYTES);
      if (numberOfJointStates > 0)
      {
         jointStates[registryID] = new double[numberOfJointStates];
      }
      else
      {
         jointStates[registryID] = null;
      }
   }
   
   public long getTimestamp()
   {
      long maxTimestamp = 0;
      for (int i = 0; i < timestamps.length; i++)
      {
         if(timestamps[i] > maxTimestamp)
         {
            maxTimestamp = timestamps[i];
         }
      }
      
      return maxTimestamp;
   }
}
