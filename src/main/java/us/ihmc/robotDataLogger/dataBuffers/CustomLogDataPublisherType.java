package us.ihmc.robotDataLogger.dataBuffers;

import logger_msgs.LogData;
import logger_msgs.LogDataType;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Custom publisher type for LogData with compression support.
 * This class extends LogData and adds efficient serialization using CDRBuffer
 * with optional compression for the data field.
 */
public class CustomLogDataPublisherType extends LogData
{
   private final int numberOfVariables;
   private final int numberOfStates;

   private final ByteBuffer compressBuffer;
   private final CompressionImplementation compressor;

   public CustomLogDataPublisherType(int numberOfVariables, int numberOfStates)
   {
      super();
      this.numberOfVariables = numberOfVariables;
      this.numberOfStates = numberOfStates;

      compressor = CompressionImplementationFactory.instance();
      if (compressor.supportsDirectOutput())
      {
         compressBuffer = null;
      }
      else
      {
         compressBuffer = ByteBuffer.allocate(compressor.maxCompressedLength(numberOfVariables * 8));
      }
   }

   private void compressDirect(ByteBuffer dataBuffer, CDRBuffer buffer)
   {
      ByteBuffer serializeBuffer = buffer.getBufferUnsafe();
      buffer.writeInt(0); // Placeholder for length of the compressed data
      int sizePosition = serializeBuffer.position() - 4; // Position for the length of the compressed data
      int written = compressor.compress(dataBuffer, serializeBuffer);
      serializeBuffer.putInt(sizePosition, written); // Write the length of the compressed data in the placeholder position
   }

   private void compressJavaBuffer(ByteBuffer dataBuffer, CDRBuffer buffer) throws IOException
   {
      compressBuffer.clear();
      compressor.compress(dataBuffer, compressBuffer);
      compressBuffer.flip();

      // Write compressed data length
      buffer.writeInt(compressBuffer.remaining());
      buffer.getBufferUnsafe().put(compressBuffer);
   }

   public void serialize(RegistrySendBuffer data, CDRBuffer buffer) throws IOException
   {
      buffer.writeLong(data.getUid());

      buffer.writeLong(data.getTimestamp());

      buffer.writeLong(data.getTransmitTime());

      buffer.writeByte(data.getType().getType());

      buffer.writeInt(data.getRegistryID());

      buffer.writeInt(data.getNumberOfVariables());

      if (data.getType().getType() == LogDataType.DATA_PACKET)
      {
         if (compressor.supportsDirectOutput())
         {
            compressDirect(data.getBuffer(), buffer);
         }
         else
         {
            compressJavaBuffer(data.getBuffer(), buffer);
         }

         // Write joint states length
         double[] jointstates = data.getJointStates();
         buffer.writeInt(jointstates.length);
         for (int i = 0; i < jointstates.length; i++)
         {
            buffer.writeDouble(jointstates[i]);
         }
      }
      else
      {
         buffer.writeInt(0);
         buffer.writeInt(0);
      }

      buffer.getBufferUnsafe().flip();
   }

   /**
    * Calculate the maximum size in bytes for serialization.
    * This accounts for all fields including compressed data.
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
      currentAlignment += compressor.maxCompressedLength(numberOfVariables * 8) + CDRBuffer.alignment(currentAlignment, 1);

      // Joint states
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length
      currentAlignment += numberOfStates * 8 + CDRBuffer.alignment(currentAlignment, 8); // double array

      return currentAlignment - initialAlignment;
   }
}