package us.ihmc.tools.compression;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionAlgorithmBenchmarkTest
{
   private final int ELEMENTS = 128000000;

   static class BenchmarkTest
   {
      double time;
      double ratio;
   }

   @Test
   public void benchmarkTestCompression() throws IOException
   {
      CompressionAlgorithm snappyCompression = new CompressionAlgorithm()
      {
         @Override
         public void compress(ByteBuffer in, ByteBuffer out) throws IOException
         {
            SnappyUtils.compress(in, out);
         }

         @Override
         public void decompress(ByteBuffer in, ByteBuffer out) throws IOException
         {
            SnappyUtils.uncompress(in, out);
         }

         @Override
         public int maxCompressedLength(int rawDataLength)
         {
            return SnappyUtils.maxCompressedLength(rawDataLength);
         }

         @Override
         public int minCompressedLength(int rawDataLength)
         {
            return ELEMENTS * 4;
         }
      };

      CompressionAlgorithm lz4Compression = new CompressionAlgorithm()
      {
         final LZ4CompressionImplementation impl = new LZ4CompressionImplementation();
         @Override
         public void compress(ByteBuffer in, ByteBuffer out)
         {
            impl.compress(in, out);
         }

         @Override
         public void decompress(ByteBuffer in, ByteBuffer out)
         {
            impl.decompress(in, out, out.limit());
         }

         @Override
         public int maxCompressedLength(int rawDataLength)
         {
            return impl.maxCompressedLength(rawDataLength);
         }

         public int minCompressedLength(int rawDataLength)
         {
            return impl.minimumDecompressedLength(rawDataLength);
         }
      };

      BenchmarkTest snappyRatioFullRandom = benchmarkTestCompression(snappyCompression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest snappyRatioHybridRandom = benchmarkTestCompression(snappyCompression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest snappyRatioRepeat = benchmarkTestCompression(snappyCompression, repeatRandomByteBufferGenerator(ELEMENTS));

      BenchmarkTest lz4RatioFullRandom = benchmarkTestCompression(lz4Compression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest lz4RatioHybridRandom = benchmarkTestCompression(lz4Compression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest lz4RatioRepeat = benchmarkTestCompression(lz4Compression, repeatRandomByteBufferGenerator(ELEMENTS));

      System.out.println("Snappy compression ratio for random input: " + snappyRatioFullRandom.ratio * 100);
      System.out.println("Snappy compression ratio for hybrid input: " + snappyRatioHybridRandom.ratio * 100);
      System.out.println("Snappy compression ratio for repeat input: " + snappyRatioRepeat.ratio * 100);

      System.out.println("Snappy compression speed for random input: " + snappyRatioFullRandom.time);
      System.out.println("Snappy compression speed for hybrid input: " + snappyRatioHybridRandom.time);
      System.out.println("Snappy compression speed for repeat input: " + snappyRatioRepeat.time);

      System.out.println("LZ4 compression ratio for random input: " + lz4RatioFullRandom.ratio * 100);
      System.out.println("LZ4 compression ratio for hybrid input: " + lz4RatioHybridRandom.ratio * 100);
      System.out.println("LZ4 compression ratio for repeat input: " + lz4RatioRepeat.ratio * 100);

      System.out.println("LZ4 compression speed for random input: " + lz4RatioFullRandom.time);
      System.out.println("LZ4 compression speed for hybrid input: " + lz4RatioHybridRandom.time);
      System.out.println("LZ4 compression speed for repeat input: " + lz4RatioRepeat.time);
   }

   public static Supplier<ByteBuffer> fullRandomByteBufferGenerator(Random random, int elements)
   {
      return () ->
      {
         ByteBuffer hybrid =  ByteBuffer.allocateDirect(elements * 4);

         for (int i = 0; i < elements; i++)
         {
            hybrid.putInt(random.nextInt());
         }

         return hybrid;
      };
   }

   public static Supplier<ByteBuffer> hybridRandomByteBufferGenerator(Random random, int elements)
   {
      return () ->
      {
         ByteBuffer hybrid =  ByteBuffer.allocateDirect(elements * 4);

         for (int i = 0; i < elements; i++)
         {
            if (i % 20 < 10)
            {
               hybrid.putInt(12);
            }
            else
            {
               hybrid.putInt(random.nextInt());
            }
         }

         return hybrid;
      };
   }

   public static Supplier<ByteBuffer> repeatRandomByteBufferGenerator(int elements)
   {
      return () ->
      {
         ByteBuffer hybrid =  ByteBuffer.allocateDirect(elements * 4);

         for (int i = 0; i < elements; i++)
         {
            hybrid.putInt(10);
         }

         return hybrid;
      };
   }
   public BenchmarkTest benchmarkTestCompression(CompressionAlgorithm algorithm, Supplier<ByteBuffer> randomGenerator) throws IOException
   {
      Stopwatch stopwatch = new Stopwatch();
      BenchmarkTest results = new BenchmarkTest();
      ByteBuffer buffer = randomGenerator.get();
      ByteBuffer bufferRatioOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
      ByteBuffer bufferOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
      ByteBuffer bufferDecompress = ByteBuffer.allocateDirect(algorithm.minCompressedLength(bufferOut.capacity()));

      buffer.flip();
      algorithm.compress(buffer, bufferRatioOut);
      bufferRatioOut.flip();

      results.ratio = (double) bufferRatioOut.limit() / buffer.limit();

      boolean decompressOnly = false;
      boolean compressOnly = false;
      if (compressOnly)
      {
         stopwatch.start();
         algorithm.compress(buffer, bufferOut);
         results.time = stopwatch.totalElapsed();
         assertEquals(0, buffer.remaining());
      }
      else if (decompressOnly)
      {
         algorithm.compress(buffer, bufferOut);
         assertEquals(0, buffer.remaining());

         bufferOut.flip();
         bufferOut.position(0);

         stopwatch.start();
         algorithm.decompress(buffer, bufferDecompress);
         results.time = stopwatch.totalElapsed();
      }
      else
      {
         buffer.flip();
         stopwatch.start();
         algorithm.compress(buffer, bufferOut);
         assertEquals(0, buffer.remaining());

         bufferOut.flip();
         bufferOut.position(0);

         algorithm.decompress(bufferOut, bufferDecompress);
         results.time = stopwatch.totalElapsed();


         bufferDecompress.flip();
         buffer.flip();
         assertEquals(ELEMENTS * 4, bufferDecompress.remaining());

         assertEquals(buffer, bufferDecompress);
      }

      return results;
   }


   private interface CompressionAlgorithm
   {
      void compress(ByteBuffer in, ByteBuffer out) throws IOException;

      void decompress(ByteBuffer in, ByteBuffer out) throws IOException;

      int maxCompressedLength(int rawDataLength);

      int minCompressedLength(int rawDataLength);
   }
}
