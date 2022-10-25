package us.ihmc.tools.compression;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.lz4.LZ4FDecompressionContext;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionAlgorithmBenchmarkTest
{
   private final int ELEMENTS = 10200;

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
         ByteBuffer hybrid = ByteBuffer.allocateDirect(elements * 4);

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
   public void testOverAndOver() throws LZ4BytedecoCompressionImplementation.LZ4Exception, IOException
   {
      for (int i = 0; i < 100; i++)
      {
         benchmarkTestCompressionAlgorithm();
      }
   }


   @Test
   public void benchmarkTestCompressionAlgorithm() throws IOException, LZ4BytedecoCompressionImplementation.LZ4Exception
   {
      CompressionAlgorithm snappyCompression = new CompressionAlgorithm()
      {
         @Override
         public double compress(ByteBuffer in, ByteBuffer out) throws IOException
         {
            SnappyUtils.compress(in, out);
            return 0;
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
         public double compress(ByteBuffer in, ByteBuffer out)
         {
            return impl.compress(in, out);
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

      CompressionAlgorithm lz4ByteDeco = new CompressionAlgorithm()
      {
         final LZ4BytedecoCompressionImplementation impl = new LZ4BytedecoCompressionImplementation();

         @Override
         public double compress(ByteBuffer in, ByteBuffer out)
         {
            Pointer inPointer = new Pointer(in);
            Pointer outPointer = new Pointer(out);

            return LZ4BytedecoCompressionImplementation.compress(in, inPointer, out, outPointer);
         }

         @Override
         public void decompress(ByteBuffer in, ByteBuffer out) throws LZ4BytedecoCompressionImplementation.LZ4Exception
         {
            LZ4FDecompressionContext decompressionContext;
            decompressionContext = LZ4BytedecoCompressionImplementation.ByteDecoLZ4CompressionImplementation();
            Pointer inPointer = new Pointer(in);
            Pointer outPointer = new Pointer(out);
            SizeTPointer inSize = new SizeTPointer(in.limit());
            SizeTPointer outSize = new SizeTPointer(out.remaining());

            LZ4BytedecoCompressionImplementation.decompress(decompressionContext, inPointer, outPointer, inSize, outSize, out, ELEMENTS);
         }

         @Override
         public int maxCompressedLength(int rawDataLength)
         {
            return impl.maxCompressedLength(rawDataLength);
         }

         @Override
         public int minCompressedLength(int rawDataLength)
         {
            return impl.minimumDecompressedLength(rawDataLength);
         }
      };

      // Snappy Compression
      BenchmarkTest snappyFullRandom = benchmarkTestCompressionAlgorithm(true, snappyCompression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("Snappy Random: " + snappyFullRandom.ratio * 100 + " time: " + snappyFullRandom.totalTime);

      BenchmarkTest snappyHybridRandom = benchmarkTestCompressionAlgorithm(false, snappyCompression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("Snappy Hybrid: " + snappyHybridRandom.ratio * 100 + " time: " + snappyHybridRandom.totalTime);

      BenchmarkTest snappyRepeat = benchmarkTestCompressionAlgorithm(false, snappyCompression, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("Snappy Repeat: " + snappyRepeat.ratio * 100 + " time: " + snappyRepeat.totalTime);

      // LZ4 1.8 Compression
      BenchmarkTest lz4FullRandom = benchmarkTestCompressionAlgorithm(true, lz4Compression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("LZ4 1.8 random: " + lz4FullRandom.ratio * 100 + " time: " + lz4FullRandom.totalTime);

      BenchmarkTest lz4HybridRandom = benchmarkTestCompressionAlgorithm(false, lz4Compression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("LZ4 1.8 hybrid: " + lz4HybridRandom.ratio * 100 + " time: " + lz4HybridRandom.totalTime);

      BenchmarkTest lz4Repeat = benchmarkTestCompressionAlgorithm(false, lz4Compression, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("LZ4 1.8 repeat: " + lz4Repeat.ratio * 100 + " time: " + lz4Repeat.totalTime);

      // LZ4 1.9 Compression
      BenchmarkTest lz4ByteDecoFullRandom = benchmarkTestCompressionAlgorithm(true, lz4ByteDeco, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("lz4 1.9 ByteDeco Random: " + lz4ByteDecoFullRandom.ratio * 100 + " time: " + lz4ByteDecoFullRandom.totalTime);

      BenchmarkTest lz4ByteDecoHybridRandom = benchmarkTestCompressionAlgorithm(false, lz4ByteDeco, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("lz4 1.9 ByteDeco Hybrid: " + lz4ByteDecoHybridRandom.ratio * 100 + " time: " + lz4ByteDecoHybridRandom.totalTime);

      BenchmarkTest lz4ByteDecoRepeat = benchmarkTestCompressionAlgorithm(false, lz4ByteDeco, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("lz4 1.9 ByteDeco Repeat: " + lz4ByteDecoRepeat.ratio * 100 + " time: " + lz4ByteDecoRepeat.totalTime);
   }

   public BenchmarkTest benchmarkTestCompressionAlgorithm(boolean warmup, CompressionAlgorithm algorithm, Supplier<ByteBuffer> randomGenerator)
         throws IOException, LZ4BytedecoCompressionImplementation.LZ4Exception
   {
      // Initial setup of variables
      Stopwatch stopwatchCompress = new Stopwatch();
      Stopwatch stopwatchDecompress = new Stopwatch();
      Stopwatch stopwatchTotal = new Stopwatch();
      BenchmarkTest results = new BenchmarkTest();
      int bytesCompressed = 0;

      ByteBuffer buffer = randomGenerator.get();
      ByteBuffer bufferOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
      ByteBuffer bufferDecompress = ByteBuffer.allocateDirect(algorithm.minCompressedLength(bufferOut.capacity()));

      // Warmup for algorithm methods, helps to optimize the JIT compiler, optimized at about 38 iterations
      if (warmup)
      {
         for (int i = 0; i < 5000; i++)
         {
            buffer.flip();
            bufferOut.clear();
            bufferDecompress.clear();

            bytesCompressed = (int) algorithm.compress(buffer, bufferOut);

            if (bufferOut.position() == 0)
            {
               bufferOut.position(bytesCompressed);
            }

            bufferOut.flip();

            algorithm.decompress(bufferOut, bufferDecompress);

            if ( buffer.position() == 0)
            {
               buffer.position(buffer.limit());
            }

            if (bufferDecompress.position() == 0)
            {
               bufferDecompress.position(bufferDecompress.limit());
            }

            assertEquals(buffer, bufferDecompress);

            for (int j = 0; j < ELEMENTS; j++)
            {
               assertEquals(buffer.get(j), bufferDecompress.get(j));
            }
         }
      }

      int iterations = 800;

      // Run benchmark on algorithm that takes an average for the ratio and time computed
      for (int i = 0; i < iterations; i++)
      {
         buffer.flip();
         bufferOut.clear();
         bufferDecompress.clear();

         stopwatchTotal.start();
         stopwatchCompress.start();

         bytesCompressed = (int) algorithm.compress(buffer, bufferOut);

         results.compressTime += stopwatchCompress.totalElapsed();

         if (bufferOut.position() == 0)
         {
            bufferOut.position(bytesCompressed);
         }

         bufferOut.flip();

         results.ratio += (double) bufferOut.limit() / buffer.limit();

         stopwatchDecompress.start();

         algorithm.decompress(bufferOut, bufferDecompress);

         results.decompressTime += stopwatchDecompress.totalElapsed();
         results.totalTime += stopwatchTotal.totalElapsed();

         if (buffer.position() == 0)
         {
            buffer.position(buffer.limit());
         }

         if (bufferDecompress.position() == 0)
         {
            bufferDecompress.position(bufferDecompress.limit());
         }
      }

      results.ratio /= iterations;
      results.compressTime /= iterations;
      results.decompressTime /= iterations;
      results.totalTime /= iterations;

      return results;
   }


   private interface CompressionAlgorithm
   {
      double compress(ByteBuffer in, ByteBuffer out) throws IOException;

      void decompress(ByteBuffer in, ByteBuffer out) throws IOException, LZ4BytedecoCompressionImplementation.LZ4Exception;

      int maxCompressedLength(int rawDataLength);

      int minCompressedLength(int rawDataLength);
   }
}
