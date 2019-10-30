package us.ihmc.tools.compression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class LZ4CompressionImplementationTest
{
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
