package us.ihmc.tools.compression;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.lz4.LZ4FDecompressionContext;
import org.bytedeco.lz4.global.lz4;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.perception.MutableBytePointer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionAlgorithmBenchmarkTest extends Pointer
{
   private final int ELEMENTS = 1024;

   // This class holds the variables that are used to measure the results of the benchmark
   static class BenchmarkTest
   {
      double compressTime = 0;
      double decompressTime = 0;
      double totalTime = 0;
      double ratio;
   }

   // Uses lambda to create a fully random ByteBuffer, the supplier is an interface that can return a result
   // There are three of these, each returns a differently filled ByteBuffer that is used in the test
   public static Supplier<ByteBuffer> fullRandomByteBufferGenerator(Random random, int elements)
   {
      return () ->
      {  // For all three of these the ByteBuffer is already passed in and just needs its space allocated
         ByteBuffer hybrid = ByteBuffer.allocateDirect(elements * 4);

         for (int i = 0; i < elements; i++)
         {
            hybrid.putInt(random.nextInt());
         }

         return hybrid;
      };
   }

   // This ByteBuffer is half random and half repetitive, useful because lz4 compression does better with repetitive data
   public static Supplier<ByteBuffer> hybridRandomByteBufferGenerator(Random random, int elements)
   {
      return () ->
      {
         ByteBuffer hybrid =  ByteBuffer.allocateDirect(elements * 4);

         for (int i = 0; i < elements; i++)
         {
            // For 10 indexes, the value 12 will be uses and the next 10 will be completely random
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

   // This ByteBuffer will be filled entirely with 10's. Useful to see how the compression ratio is affected
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
      public int minDecompressedLength(int rawDataLength)
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

      public int minDecompressedLength(int rawDataLength)
      {
         return impl.minimumDecompressedLength(rawDataLength);
      }
   };

   CompressionAlgorithm lz4BytedecoCompression = new CompressionAlgorithm()
   {
      // Creates the variables needed to compress and decompress, they are only created when the algorithm is used
      final LZ4BytedecoCompressionImplementation impl = new LZ4BytedecoCompressionImplementation();
      final MutableBytePointer inPointer = new MutableBytePointer();
      final MutableBytePointer outPointer = new MutableBytePointer();
      final SizeTPointer inSize = new SizeTPointer(1);
      final SizeTPointer outSize = new SizeTPointer(1);

      // decompressionContext is used to check for any errors with the LZ4 decompression
      LZ4FDecompressionContext decompressionContext;

      @Override
      public double compress(ByteBuffer in, ByteBuffer out)
      {
         //The wrapMutableBytePointer sets the address, limit, and capacity of a MutableBytePointer to the ByteBuffer that's passed in
         inPointer.wrapMutableBytePointer(in);
         outPointer.wrapMutableBytePointer(out);

         return LZ4BytedecoCompressionImplementation.compress(in, inPointer, out, outPointer);
      }

      @Override
      public void decompress(ByteBuffer in, ByteBuffer out) throws LZ4BytedecoCompressionImplementation.LZ4Exception
      {
         // The decompress method requires decompressionContext to check for errors
         decompressionContext = LZ4BytedecoCompressionImplementation.BytedecoLZ4CompressionImplementation();

         inSize.put(in.limit());
         outSize.put(out.remaining());

         //The wrapMutableBytePointer sets the address, limit, and capacity of a MutableBytePointer to the ByteBuffer that's passed in
         inPointer.wrapMutableBytePointer(in);
         outPointer.wrapMutableBytePointer(out);

         LZ4BytedecoCompressionImplementation.decompress(decompressionContext, inPointer, outPointer, inSize, outSize, out, ELEMENTS);
         lz4.LZ4F_freeDecompressionContext(decompressionContext);
      }

      @Override
      public int maxCompressedLength(int rawDataLength)
      {
         return impl.maxCompressedLength(rawDataLength);
      }

      @Override
      public int minDecompressedLength(int rawDataLength)
      {
         return impl.minimumDecompressedLength(rawDataLength);
      }
   };

   // There are several odd bugs with using the compression algorithms, this test was used because often times the compression
   // would work for one usage but not the next one. This confirms that the test works over and over again without failing
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
      // Snappy Compression for fullRandom, hybridRandom, and repetitive
      BenchmarkTest snappyFullRandom = benchmarkTestCompressionAlgorithm(true, snappyCompression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("Snappy Random: " + snappyFullRandom.ratio * 100 + " time: " + snappyFullRandom.totalTime);

      BenchmarkTest snappyHybridRandom = benchmarkTestCompressionAlgorithm(false, snappyCompression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("Snappy Hybrid: " + snappyHybridRandom.ratio * 100 + " time: " + snappyHybridRandom.totalTime);

      BenchmarkTest snappyRepeat = benchmarkTestCompressionAlgorithm(false, snappyCompression, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("Snappy Repeat: " + snappyRepeat.ratio * 100 + " time: " + snappyRepeat.totalTime);

      // LZ4 1.8 Compression for fullRandom, hybridRandom, and repetitive
      BenchmarkTest lz4FullRandom = benchmarkTestCompressionAlgorithm(true, lz4Compression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("LZ4 1.8 random: " + lz4FullRandom.ratio * 100 + " time: " + lz4FullRandom.totalTime);

      BenchmarkTest lz4HybridRandom = benchmarkTestCompressionAlgorithm(false, lz4Compression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("LZ4 1.8 hybrid: " + lz4HybridRandom.ratio * 100 + " time: " + lz4HybridRandom.totalTime);

      BenchmarkTest lz4Repeat = benchmarkTestCompressionAlgorithm(false, lz4Compression, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("LZ4 1.8 repeat: " + lz4Repeat.ratio * 100 + " time: " + lz4Repeat.totalTime);

      // LZ4 1.9 Compression for fullRandom, hybridRandom, and repetitive
      BenchmarkTest lz4BytedecoFullRandom = benchmarkTestCompressionAlgorithm(true,
                                                                              lz4BytedecoCompression, fullRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("lz4 1.9 Bytedeco Random: " + lz4BytedecoFullRandom.ratio * 100 + " time: " + lz4BytedecoFullRandom.totalTime);

      BenchmarkTest lz4BytedecoHybridRandom = benchmarkTestCompressionAlgorithm(false,
                                                                                lz4BytedecoCompression, hybridRandomByteBufferGenerator(new Random(1234), ELEMENTS));
      System.out.println("lz4 1.9 Bytedeco Hybrid: " + lz4BytedecoHybridRandom.ratio * 100 + " time: " + lz4BytedecoHybridRandom.totalTime);

      BenchmarkTest lz4BytedecoRepeat = benchmarkTestCompressionAlgorithm(false, lz4BytedecoCompression, repeatRandomByteBufferGenerator(ELEMENTS));
      System.out.println("lz4 1.9 Bytedeco Repeat: " + lz4BytedecoRepeat.ratio * 100 + " time: " + lz4BytedecoRepeat.totalTime);
   }

   public BenchmarkTest benchmarkTestCompressionAlgorithm(boolean warmup, CompressionAlgorithm algorithm, Supplier<ByteBuffer> randomGenerator)
         throws IOException, LZ4BytedecoCompressionImplementation.LZ4Exception
   {
      // Initial setup of variables
      Stopwatch stopwatchCompress = new Stopwatch();
      Stopwatch stopwatchDecompress = new Stopwatch();
      Stopwatch stopwatchTotal = new Stopwatch();
      BenchmarkTest results = new BenchmarkTest();
      int bytesCompressed;

      // Warmup for algorithm methods, helps to optimize the JIT compiler and is only called if warmup is set as true in the parameters
      if (warmup)
      {
         for (int i = 0; i < 25000; i++)
         {
            // Each ByteBuffer is filled with a random generator that is passed in during the method call
            // This is in the for loop so the supplier can get new values at each iteration of the for loop using get and a set seed
            ByteBuffer buffer = randomGenerator.get();
            ByteBuffer bufferOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
            ByteBuffer bufferDecompress = ByteBuffer.allocateDirect(algorithm.minDecompressedLength(bufferOut.capacity()));

            // When using the supplier the position of this buffer gets moved and needs to be reset before compress is called
            buffer.flip();

            // Compresses data into bufferOut and returns the number of bytes that were compressed
            bytesCompressed = (int) algorithm.compress(buffer, bufferOut);

            // LZ4 1.9 uses pointers to implement so the positions of the buffers don't actually change, this ensures that the positions get updates
            if (bufferOut.position() == 0)
            {
               bufferOut.position(bytesCompressed);
            }

            bufferOut.flip();

            // Decompress the compressed data into bufferDecompress
            algorithm.decompress(bufferOut, bufferDecompress);

            // Tests to see if the initial data and the decompressed data are the same, this makes sure the test actually works
            for (int j = 0; j < ELEMENTS; j++)
            {
               assertEquals(buffer.get(j), bufferDecompress.get(j));
            }
         }
      }

      int iterations = 800;

      // Run benchmark on algorithm that takes an average for the ratio and time computed
      // This loop is the same as the warmup loop but keeps track of time for the benchmark
      for (int i = 0; i < iterations; i++)
      {
         // Each ByteBuffer is filled with a random generator that is passed in during the method call
         // This is in the for loop so the supplier can get new values at each iteration of the for loop using get and a set seed
         ByteBuffer buffer = randomGenerator.get();
         ByteBuffer bufferOut = ByteBuffer.allocateDirect(algorithm.maxCompressedLength(buffer.capacity()));
         ByteBuffer bufferDecompress = ByteBuffer.allocateDirect(algorithm.minDecompressedLength(bufferOut.capacity()));

         buffer.flip();

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
      }

      // After the benchmark has finished, the times and ratio's get divided by the number of time the for loop ran, this gives results for a single use
      results.ratio /= iterations;
      results.compressTime /= iterations;
      results.decompressTime /= iterations;
      results.totalTime /= iterations;

      return results;
   }

   // This interface is used to define each of our compression algorithms
   private interface CompressionAlgorithm
   {
      // Takes in two ByteBuffers, the first is full of data that will be compressed into the second buffer
      double compress(ByteBuffer in, ByteBuffer out) throws IOException;

      // Takes in two ByteBuffers, the first contains compressed data, which will be decompressed into the second buffer
      void decompress(ByteBuffer in, ByteBuffer out) throws IOException, LZ4BytedecoCompressionImplementation.LZ4Exception;

      // Returns the max amount of space the given data can be compressed into
      int maxCompressedLength(int rawDataLength);

      // Returns the minimum amount of space the data can be decompressed out to
      int minDecompressedLength(int rawDataLength);
   }
}
