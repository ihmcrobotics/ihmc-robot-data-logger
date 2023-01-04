package us.ihmc.tools.compression;

import java.nio.ByteBuffer;
import java.util.Random;

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
