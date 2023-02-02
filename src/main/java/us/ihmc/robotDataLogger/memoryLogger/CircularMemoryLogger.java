package us.ihmc.robotDataLogger.memoryLogger;

import java.io.File;
import java.nio.ByteBuffer;
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
   
   private final MemoryBufferEntry[] circularBuffer;
   
   private final File logDirectory;
   
   private volatile boolean isRecording;
   
   /*
    * 40 bit timestamp 
    * 24 bit index
    * 
    * Allow atomic increase of index and last timestamp. It is crazy
    * 
    */
   private final AtomicLong timestampAndIndex = new AtomicLong();
   
   public CircularMemoryLogger(File logDirectory, int numberOfEntries)
   {
      if(numberOfEntries > 16777215)
      {
         throw new RuntimeException("Memory logger supports a maximum of 16777215 entries, using 24bit index");
      }
      if (numberOfEntries <= 0)
      {
         throw new RuntimeException("Memory logger needs at least 1 entry");
      }
      
      this.logDirectory = logDirectory;
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
      
      if(circularBuffer.length <= numberOfRegistries + 1)
      {
         throw new RuntimeException("Memory logger needs at " + (numberOfRegistries + 2) + " entries");
      }
      
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
      isRecording = true;
   }
   
   /**
    * Look back in the buffer to find the buffer with the closest timestamp that is higher than or equal to the current
    * 
    * Only looks back to 10% of the buffer length for optimization and race condition avoidance
    * 
    * if the timestamp is not found, return -1
    * 
    * @param currentIndex
    * @param timestamp
    * @return
    */
   private int getIndexForTimestamp(int currentIndex, long timestamp)
   {
      int index = currentIndex;      
      for(int i = 1; i < circularBuffer.length/10; i++)
      {
         int nextIndex = currentIndex - i;
         if(nextIndex < 0)
         {
            nextIndex = circularBuffer.length + nextIndex;
         }
         
         long ts = circularBuffer[nextIndex].getTimestamp();
         
         if(ts < timestamp)   
         {
            return index;
         }
         else if (ts == timestamp)
         {
            return nextIndex;
         }
         else
         {
            index = nextIndex;
         }
      }
      return -1;
   }

   @Override
   public void updateBuffer(int bufferID, RegistrySendBuffer buffer)
   {
      int bufferIndex = -1;
      long adjustedTimestamp = -1;
      // Figure out if we have to advance the write index
      for(int i = 0; i < 1000; i++) // Use a for loop to avoid deadlock
      {
         // Return once recording is done to avoid creating new blocks
         if(!isRecording)
         {
            return;
         }
         
         long currentValue = timestampAndIndex.get();
         
         int currentIndex = (int) (currentValue & 0xFFFFFF);
         long currentTimestamp = currentValue >> 24;
         
         
         long bufferTimestamp = buffer.getTimestamp() & 0xFFFFFFFFFFL;
         if(currentTimestamp == bufferTimestamp)
         {
            bufferIndex = currentIndex;
            adjustedTimestamp = buffer.getTimestamp();
            break;
         }
         else if (currentTimestamp > bufferTimestamp)
         {

            bufferIndex = getIndexForTimestamp(currentIndex, bufferTimestamp);
                        
            if(bufferIndex > 0)
            {
               adjustedTimestamp = circularBuffer[bufferIndex].getTimestamp();
               break;
            }
            else
            {
               return;
            }
         }
         else 
         {
            int nextIndex = (currentIndex + 1) % circularBuffer.length;
            long nextValue = bufferTimestamp << 24 | nextIndex;
            
            if(timestampAndIndex.compareAndSet(currentValue, nextValue))
            {
               bufferIndex = nextIndex;
               adjustedTimestamp = buffer.getTimestamp();
               break;
            }
         }
      }
      
      if(bufferIndex < 0)
      {
         LogTools.info("Timeout");
         return;
      }
      
      
      MemoryBufferEntry nextBuffer = circularBuffer[bufferIndex];
      
      // Use the adjusted timestamp instead of the actual buffer timestamp so that a field is written if the timestamp matches the maximum timestamp in an entry
      nextBuffer.timestamps[bufferID] = adjustedTimestamp;
      
      ByteBuffer variableData = buffer.getBuffer();
      variableData.position(0);
      ByteBuffer nextVariableData = nextBuffer.variables[bufferID];
      nextVariableData.clear();
      nextVariableData.put(variableData);
      
      int jointStates = buffer.getJointStates().length;
      if(jointStates > 0)
      {
         System.arraycopy(buffer.getJointStates(), 0, nextBuffer.jointStates[bufferID], 0, jointStates);
      }
      
   }

   @Override
   public void close()
   {
      isRecording = false;
      
      long currentValue = timestampAndIndex.get();
      
      int currentIndex = (int) (currentValue & 0xFFFFFF);
      
      // Skip number of registries + 1 packets, because each thread could write one more log field
      int skipPackets = numberOfRegistries + 1;
      
      
      MemoryBufferEntry previousBufferEntry = new MemoryBufferEntry(numberOfRegistries);
      
      
      MemoryLogWriter writer = new MemoryLogWriter(dataserverContent, logDirectory);
      
      for(int i = currentIndex + skipPackets; i < currentIndex + circularBuffer.length; i++)
      {
         int writeIndex = i % circularBuffer.length;
         
         if(writeIndex == currentIndex)
         {
            throw new RuntimeException();
         }
         
         MemoryBufferEntry entry = circularBuffer[writeIndex];
         long entryTimestamp = entry.getTimestamp();
         
         // Skip empty entries
         if(entryTimestamp == 0)
         {
            continue;
         }
         
         
         // Write data for slower threads on all buffer entries
         for(int r = 0; r < numberOfRegistries; r++)
         {
            long bufferTime = entry.timestamps[r];
            
            if(bufferTime != entryTimestamp)
            {
               if(previousBufferEntry.variables[r] != null)
               {
                  entry.variables[r] = previousBufferEntry.variables[r];
               }
               if(previousBufferEntry.jointStates[r] != null)
               {
                  entry.jointStates[r] = previousBufferEntry.jointStates[r];
               }
            }
            else
            {
               previousBufferEntry.variables[r] = entry.variables[r];
               previousBufferEntry.jointStates[r] = entry.jointStates[r];
            }
         }
         
         
         
         writer.addBuffer(entry);
         
         
      }

      writer.finish();
   }


}
