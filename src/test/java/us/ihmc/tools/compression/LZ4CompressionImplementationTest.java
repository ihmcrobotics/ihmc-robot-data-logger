package us.ihmc.tools.compression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

public class LZ4CompressionImplementationTest
{
   int ELEMENTS = 1024;
   Random random = new Random(1234);

   @Test
   public void testLZ4DataDirectBuffer()
   {
      final LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);

      for (int i = 0; i < ELEMENTS; i++)
      {
         data.putInt(random.nextInt());
      }

      ByteBuffer target = ByteBuffer.allocateDirect(impl.maxCompressedLength(ELEMENTS * 4));

      for (int i = 0; i < 6; i++)
      {
         data.clear();
         target.clear();
         impl.compress(data, target);
      }
   }

   @Test
   public void testLZ4DataBuffer()
   {
      final LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      ByteBuffer data = ByteBuffer.allocate(ELEMENTS * 4);

      for (int i = 0; i < ELEMENTS; i++)
      {
         data.putInt(random.nextInt());
      }

      ByteBuffer target = ByteBuffer.allocateDirect(impl.maxCompressedLength(ELEMENTS * 4));

      for (int i = 0; i < 6; i++)
      {
         data.clear();
         target.clear();
         impl.compress(data, target);
      }
   }

   @Test
   public void testLength()
   {
      LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      Random random = new Random(12597651l);
      for (int i = 0; i < 1000; i++)
      {
         int test = random.nextInt(65000);
         int max = impl.maxCompressedLength(test);
         int min = impl.minimumDecompressedLength(max);

         assertTrue(test == min || test - 1 == min, "Got: " + min + ", expected " + test + " or " + test + "-1");

      }

   }
}
