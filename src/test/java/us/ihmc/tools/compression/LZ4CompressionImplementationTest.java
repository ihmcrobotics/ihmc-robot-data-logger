package us.ihmc.tools.compression;

import java.nio.ByteBuffer;
import java.util.Random;

import io.netty.buffer.ByteBuf;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LZ4CompressionImplementationTest
{
   int ELEMENTS = 1024;
   Random random = new Random(1234);

   public void fillRandomData(ByteBuffer buffer)
   {
      for (int i = 0; i < ELEMENTS; i++)
      {
         buffer.putInt(random.nextInt());
      }
   }

   public void checkDataIsDifferent(ByteBuffer data, ByteBuffer target)
   {
      int dataSum = 0;
      int targetSum = 0;

      for (int i = 0; i < data.capacity(); i++)
      {
         dataSum += data.get(i);
      }

      for (int i = 0; i < target.capacity(); i++)
      {
         targetSum += target.get(i);
      }

      Assertions.assertTrue(dataSum != targetSum);
   }

   public void compressLZ4WithDesiredMethod(LZ4Compressor compressor, ByteBuffer data, ByteBuffer target)
   {
      for (int i = 0; i < 12; i++)
      {
         data.clear();
         target.clear();
         compressor.compress(data, target);
         checkDataIsDifferent(data, target);
      }
   }

   @Test
   public void testLZ4FactorySafeInstance()
   {
      LZ4Compressor safeCompressor = LZ4Factory.safeInstance().fastCompressor();

      //Compress using direct ByteBuffers
      ByteBuffer dataDirect = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(safeCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(dataDirect);
      compressLZ4WithDesiredMethod(safeCompressor, dataDirect, targetDirect);

      //Compress using regular ByteBuffers
      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);
      ByteBuffer target = ByteBuffer.allocate(safeCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(data);
      compressLZ4WithDesiredMethod(safeCompressor, data, target);
   }

   @Test
   public void testLZ4FactoryUnsafeInstance()
   {
      LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInstance().fastCompressor();

      //Compress using direct ByteBuffers
      ByteBuffer dataDirect = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(dataDirect);
      compressLZ4WithDesiredMethod(unsafeCompressor, dataDirect, targetDirect);

      //Compress using regular ByteBuffers
      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);
      ByteBuffer target = ByteBuffer.allocate(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(data);
      compressLZ4WithDesiredMethod(unsafeCompressor, data, target);
   }

   @Test
   public void testLZ4FactoryNativeInstance()
   {
      LZ4Compressor jniCompressor = LZ4Factory.nativeInstance().fastCompressor();

      //Compress using direct ByteBuffers
      ByteBuffer dataDirect = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(jniCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(dataDirect);
      compressLZ4WithDesiredMethod(jniCompressor, dataDirect, targetDirect);

      //Compress using regular ByteBuffers
      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);
      ByteBuffer target = ByteBuffer.allocate(jniCompressor.maxCompressedLength(ELEMENTS * 4));
      fillRandomData(data);
      compressLZ4WithDesiredMethod(jniCompressor, data, target);
   }

   @Test
   public void testLZ4DataDirectBuffer()
   {
      final LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      ByteBuffer data = ByteBuffer.allocateDirect(ELEMENTS * 4);
      fillRandomData(data);

      ByteBuffer target = ByteBuffer.allocateDirect(impl.maxCompressedLength(ELEMENTS * 4));

      for (int i = 0; i < 6; i++)
      {
         data.clear();
         target.clear();
         impl.compress(data, target);
      }

      checkDataIsDifferent(data, target);
   }

   @Test
   public void testLZ4DataBuffer()
   {
      final LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);
      fillRandomData(data);

      ByteBuffer target = ByteBuffer.allocateDirect(impl.maxCompressedLength(ELEMENTS * 4));

      for (int i = 0; i < 6; i++)
      {
         data.clear();
         target.clear();
         impl.compress(data, target);
      }

      checkDataIsDifferent(data, target);
   }

   @Test
   public void testLength()
   {
      LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      Random random = new Random(12597651L);
      for (int i = 0; i < 1000; i++)
      {
         int test = random.nextInt(65000);
         int max = impl.maxCompressedLength(test);
         int min = impl.minimumDecompressedLength(max);

         assertTrue(test == min || test - 1 == min, "Got: " + min + ", expected " + test + " or " + test + "-1");

      }

   }
}
