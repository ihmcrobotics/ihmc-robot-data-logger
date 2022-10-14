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
      double compressTime = 0;
      double decompressTime = 0;
      double totalTime = 0;
      double ratio;
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


   @Test
   public void benchmarkTestCompressionAlgorithm() throws IOException
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

      BenchmarkTest snappyFullRandom = benchmarkTestCompressionAlgorithm(snappyCompression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest snappyHybridRandom = benchmarkTestCompressionAlgorithm(snappyCompression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest snappyRepeat = benchmarkTestCompressionAlgorithm(snappyCompression, repeatRandomByteBufferGenerator(ELEMENTS));

      BenchmarkTest lz4FullRandom = benchmarkTestCompressionAlgorithm(lz4Compression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest lz4HybridRandom = benchmarkTestCompressionAlgorithm(lz4Compression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      BenchmarkTest lz4Repeat = benchmarkTestCompressionAlgorithm(lz4Compression, repeatRandomByteBufferGenerator(ELEMENTS));

      System.out.println("Snappy Random: " + snappyFullRandom.ratio * 100 + " time: " + snappyFullRandom.totalTime);
      System.out.println("Snappy Hybrid: " + snappyHybridRandom.ratio * 100 + " time: " + snappyHybridRandom.totalTime);
      System.out.println("Snappy Repeat: " + snappyRepeat.ratio * 100 + " time: " + snappyRepeat.totalTime);

      System.out.println("LZ4 random: " + lz4FullRandom.ratio * 100 + " time: " + lz4FullRandom.totalTime);
      System.out.println("LZ4 hybrid: " + lz4HybridRandom.ratio * 100 + " time: " + lz4HybridRandom.totalTime);
      System.out.println("LZ4 repeat: " + lz4Repeat.ratio * 100 + " time: " + lz4Repeat.totalTime);
   }


   public BenchmarkTest benchmarkTestCompressionAlgorithm(CompressionAlgorithm algorithm, Supplier<ByteBuffer> randomGenerator) throws IOException
   {
      // Initial setup of variables
      Stopwatch stopwatchCompress = new Stopwatch();
      Stopwatch stopwatchDecompress = new Stopwatch();
      Stopwatch stopwatchTotal = new Stopwatch();
      BenchmarkTest results = new BenchmarkTest();
      ByteBuffer buffer = randomGenerator.get();
      ByteBuffer bufferOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
      ByteBuffer bufferDecompress = ByteBuffer.allocateDirect(algorithm.minCompressedLength(bufferOut.capacity()));

      // Warmup for algorithm methods, helps to optimize the JIT compiler
      for (int i = 0; i < 500; i++)
      {
         buffer.flip();
         bufferOut.clear();
         bufferDecompress.clear();

         algorithm.compress(buffer, bufferOut);

         assertEquals(0, buffer.remaining());

         bufferOut.flip();
         bufferOut.position(0);

         algorithm.decompress(bufferOut, bufferDecompress);

         assertEquals(buffer, bufferDecompress);
      }

      int iterations = 100;

      // Run benchmark on algorithm that takes an average for the ratio and time computed
      for (int i = 0; i < iterations; i++)
      {
         buffer.flip();
         bufferOut.clear();
         bufferDecompress.clear();

         stopwatchTotal.start();
         stopwatchCompress.start();

         algorithm.compress(buffer, bufferOut);

         results.compressTime += stopwatchCompress.totalElapsed();

         bufferOut.flip();
         bufferOut.position(0);

         results.ratio += (double) bufferOut.limit() / buffer.limit();

         stopwatchDecompress.start();

         algorithm.decompress(bufferOut, bufferDecompress);

         results.decompressTime += stopwatchDecompress.totalElapsed();
         results.totalTime += stopwatchTotal.totalElapsed();
      }

      results.ratio /= iterations;
      results.compressTime /= iterations;
      results.decompressTime /= iterations;
      results.totalTime /= iterations;

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
