package us.ihmc.robotDataLogger.dataBuffers;

import logger_msgs.msg.dds.LogData;
import logger_msgs.msg.dds.LogDataType;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;

import java.nio.ByteBuffer;

/**
 * Custom subscriber type for LogData with decompression support.
 * This class extends LogData and adds efficient deserialization using CDRBuffer
 * with decompression for the data field.
 */
public class CustomLogDataSubscriberType extends LogData
{
   private final int numberOfVariables;
   private final int numberOfStates;

   private final CompressionImplementation compressor;

   public CustomLogDataSubscriberType(int maxNumberOfVariables, int maxNumberOfStates)
   {
      super();
      this.numberOfVariables = maxNumberOfVariables;
      this.numberOfStates = maxNumberOfStates;

      compressor = CompressionImplementationFactory.instance();
   }

   /**
    * Deserialize from CDRBuffer into a RegistryReceiveBuffer.
    * This method handles decompression of the variable data.
    */
   public void deserialize(CDRBuffer cdrBuffer, RegistryReceiveBuffer buffer)
   {
      // Deserialize using parent class method
      super.deserialize(cdrBuffer);

      // Copy fields to RegistryReceiveBuffer
      buffer.setUid(getUid());
      buffer.setTimestamp(getTimestamp());
      buffer.setTransmitTime(getTransmitTime());
      buffer.getType().setType(getType());
      buffer.setRegistryID(getRegistry());
      buffer.setNumberOfVariables(getNumberOfVariables());

      // Handle data decompression for DATA_PACKET types
      if (getType() == LogDataType.DATA_PACKET)
      {
         // Get compressed data
         int compressedSize = getData().size();
         ByteBuffer compressedBuffer = buffer.allocateBuffer(compressedSize);

         // Copy compressed data from IDLByteSequence to ByteBuffer
         ByteBuffer sourceBuffer = getData().getBuffer();
         sourceBuffer.position(0);
         sourceBuffer.limit(compressedSize);
         compressedBuffer.put(sourceBuffer);
         compressedBuffer.flip();

         // Copy joint states
         int stateLength = getJointStates().size();
         double[] states = buffer.allocateStates(stateLength);
         for (int i = 0; i < stateLength; i++)
         {
            states[i] = getJointStates().getBuffer().get(i);
         }
      }
   }

   /**
    * Calculate the maximum size in bytes for deserialization.
    */
   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // uid
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // timestamp
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // transmitTime
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // type
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // registry
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // offset
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // numberOfVariables

      // Maximum compressed data size
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length
      currentAlignment += compressor.maxCompressedLength(numberOfVariables * 8);

      // Joint states
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length
      currentAlignment += numberOfStates * 8; // double array

      return currentAlignment - initialAlignment;
   }
}