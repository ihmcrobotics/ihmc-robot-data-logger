package us.ihmc.robotDataLogger.memoryLogger;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBuffer;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.robotDataLogger.interfaces.BufferListenerInterface;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;

public class CircularMemoryLogger implements BufferListenerInterface
{
   private DataServerServerContent dataserverContent = null;
   
   private int previousBufferID = -1;
   private int numberOfRegistries;
   
   private class MemoryBufferEntry
   {
      private final ByteBuffer variables[];
      private final double[][] jointStates;
      private final long[] timestamps;
      
      
      public MemoryBufferEntry(int numberOfBuffers)
      {
         variables = new ByteBuffer[numberOfBuffers];
         jointStates = new double[numberOfBuffers][];
         timestamps = new long[numberOfBuffers];         
      }
      
      public void initializeRegistry(int registryID, int numberOfVariables, int numberOfJointStates)
      {
         variables[registryID] = ByteBuffer.allocateDirect(numberOfVariables * Long.BYTES);
         if(numberOfJointStates > 0)
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
         for(int i = 0; i < timestamps.length; i++)
         {
            if(timestamps[i] > maxTimestamp)
            {
               maxTimestamp = timestamps[i];
            }
         }
         
         return maxTimestamp;
      }
   }
   
   private final MemoryBufferEntry[] circularBuffer;
   
   
   /*
    * 40 bit timestamp 
    * 24 bit index
    * 
    * Allow atomic increase of index and last timestamp. It is crazy
    * 
    */
   private final AtomicLong timestampAndIndex = new AtomicLong();
   
   public CircularMemoryLogger(int numberOfEntries)
   {
      if(numberOfEntries > 16777215)
      {
         throw new RuntimeException("Memory logger supports a maximum of 16777215 entries, using 24bit index");
      }
      
      circularBuffer = new MemoryBufferEntry[numberOfEntries];
   }
   
   
   @Override
   public void setContent(DataServerServerContent content)
   {
      this.dataserverContent = content;
   }

   @Override
   public void allocateBuffers(int numberOfRegistries)
   {
      this.numberOfRegistries = numberOfRegistries;
      
      for(int i = 0; i < circularBuffer.length; i++)
      {
         circularBuffer[i] = new MemoryBufferEntry(numberOfRegistries);
      }
   }

   @Override
   public void addBuffer(int bufferID, RegistrySendBufferBuilder builder)
   {
      if(previousBufferID + 1 != bufferID)
      {
         throw new RuntimeException("Non sequential buffer IDs");
      }
      previousBufferID = bufferID;
      
      for(int i = 0; i < circularBuffer.length; i++)
      {
         circularBuffer[i].initializeRegistry(bufferID, builder.getNumberOfVariables(), builder.getNumberOfJointStates());
      }
   }
   

   @Override
   public void start()
   {
      
   }

   @Override
   public void updateBuffer(int bufferID, RegistrySendBuffer buffer)
   {
      int bufferIndex;
      // Figure out if we have to advance the write index
      while(true)
      {
         long currentValue = timestampAndIndex.get();
         
         int currentIndex = (int) (currentValue & 0xFFFFFF);
         long currentTimestamp = currentValue >> 24;
         
         
         long bufferTimestamp = buffer.getTimestamp() & 0xFFFFFFFFFFL;
         if(currentTimestamp == bufferTimestamp)
         {
            bufferIndex = currentIndex;
            break;
         }
         else if (currentTimestamp > bufferTimestamp)
         {
            LogTools.info("Skipping log");
            return;
         }
         else 
         {
            int nextIndex = (currentIndex + 1) % circularBuffer.length;
            long nextValue = bufferTimestamp << 24 | nextIndex;
            
            if(timestampAndIndex.compareAndSet(currentValue, nextValue))
            {
               bufferIndex = nextIndex;
               break;
            }
         }
      }
      
      
      MemoryBufferEntry nextBuffer = circularBuffer[bufferIndex];
      
      nextBuffer.timestamps[bufferID] = buffer.getTimestamp();
      
      ByteBuffer variableData = buffer.getBuffer();
      variableData.position(0);
      nextBuffer.variables[bufferID].put(variableData);
      
      int jointStates = buffer.getJointStates().length;
      if(jointStates > 0)
      {
         System.arraycopy(buffer.getJointStates(), 0, nextBuffer.jointStates[bufferID], 0, jointStates);
      }
      
   }

   @Override
   public void close()
   {
      // TODO Auto-generated method stub
      
   }


}
