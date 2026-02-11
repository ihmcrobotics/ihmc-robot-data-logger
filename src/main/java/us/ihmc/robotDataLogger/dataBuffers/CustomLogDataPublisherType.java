package us.ihmc.robotDataLogger.dataBuffers;

import logger_msgs.msg.dds.LogData;
import logger_msgs.msg.dds.LogDataType;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;

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

   /**
    * Serialize a RegistrySendBuffer into the provided CDRBuffer.
    * This method handles compression of the variable data.
    */
   public void serialize(RegistrySendBuffer buffer, CDRBuffer cdrBuffer)
   {
      // Set fields from the RegistrySendBuffer
      setUid(buffer.getUid());
      setTimestamp(buffer.getTimestamp());
      setTransmitTime(buffer.getTransmitTime());
      setType(buffer.getType().getType());
      setRegistry(buffer.getRegistryID());
      setNumberOfVariables(buffer.getNumberOfVariables());

      // Compress the data
      ByteBuffer variableBuffer = buffer.getBuffer();
      int compressedSize;

      if (compressor.supportsDirectOutput())
      {
         // Compress directly into the data sequence
         getData().clear();
         getData().ensureMinCapacity(compressor.maxCompressedLength(variableBuffer.remaining()));
         compressedSize = compressor.compress(variableBuffer, getData().getBuffer());
         getData().getBuffer().limit(compressedSize);
      }
      else
      {
         // Compress into temporary buffer, then copy
         compressBuffer.clear();
         compressedSize = compressor.compress(variableBuffer, compressBuffer);
         compressBuffer.flip();

         getData().clear();
         getData().ensureMinCapacity(compressedSize);
         getData().getBuffer().put(compressBuffer);
         getData().getBuffer().limit(compressedSize);
      }

      setOffset(0);

      // Copy joint states
      double[] jointStateArray = buffer.getJointStates();
      getJointStates().clear();
      getJointStates().ensureMinCapacity(jointStateArray.length);
      for (int i = 0; i < jointStateArray.length; i++)
      {
         getJointStates().getBuffer().put(i, jointStateArray[i]);
      }

      // Now serialize using the parent class method
      super.serialize(cdrBuffer);
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
      currentAlignment += compressor.maxCompressedLength(numberOfVariables * 8);

      // Joint states
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length
      currentAlignment += numberOfStates * 8; // double array

      return currentAlignment - initialAlignment;
   }
}