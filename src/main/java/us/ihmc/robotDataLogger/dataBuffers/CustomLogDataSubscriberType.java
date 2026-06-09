package us.ihmc.robotDataLogger.dataBuffers;

import logger_msgs.LogData;
import logger_msgs.LogDataType;
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
    * Wire format matches {@link CustomLogDataPublisherType}, not generated {@link LogData#deserialize}.
    */
   public void deserialize(CDRBuffer cdrBuffer, RegistryReceiveBuffer buffer)
   {
      cdrBuffer.readPayloadHeader();

      buffer.setUid(cdrBuffer.readLong());
      buffer.setTimestamp(cdrBuffer.readLong());
      buffer.setTransmitTime(cdrBuffer.readLong());

      byte packetType = cdrBuffer.readByte();
      buffer.getType().setType(packetType);

      buffer.setRegistryID(cdrBuffer.readInt());
      buffer.setNumberOfVariables(cdrBuffer.readInt());

      if (packetType == LogDataType.DATA_PACKET)
      {
         int compressedSize = cdrBuffer.readInt();
         ByteBuffer compressedBuffer = buffer.allocateBuffer(compressedSize);
         ByteBuffer sourceBuffer = cdrBuffer.getBufferUnsafe();
         for (int i = 0; i < compressedSize; i++)
         {
            compressedBuffer.put(sourceBuffer.get());
         }
         compressedBuffer.flip();

         int stateLength = cdrBuffer.readInt();
         double[] states = buffer.allocateStates(stateLength);
         for (int i = 0; i < stateLength; i++)
         {
            states[i] = cdrBuffer.readDouble();
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
