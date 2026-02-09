package us.ihmc.robotDataLogger.dataBuffers;

import logger_msgs.msg.dds.LogData;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;

import java.nio.ByteBuffer;

/**
 * Topic data type of the struct "LogData" defined in "LogData.idl". Use this class to provide the
 * TopicDataType to a Participant. This file has been modified from the generated version to provide
 * higher performance.
 */
public class CustomLogDataPublisherType extends LogData
{
   public static final java.lang.String name = "logger_msgs::msg::dds_::LogData_";

   private final int numberOfVariables;
   private final int numberOfStates;

   private final ByteBuffer compressBuffer;
   private final CompressionImplementation compressor;

   public CustomLogDataPublisherType(int numberOfVariables, int numberOfStates)
   {
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

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      return super.calculateSizeBytes(currentAlignment);
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      super.serialize(buffer);
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      super.deserialize(buffer);
   }
}