package us.ihmc.tools.compression;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.jcodec.common.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

public class CompressionAlgorithmBenchmarkTest
{
   private static final Random rand = new Random(98753244356L);

   private final boolean COMPRESS_ONLY = false;
   private final boolean DECOMPRESS_ONLY = false;
   private final int ELEMENTS = 128000000;

   private static class BenchmarkResult
   {
      private double compressionRatioRandom;
      private double compressionRatioHybrid;
      private double compressionRatioRepeat;

      private double compressionTime;
   }

   @Test
   // This test is meant to analyze the compression ratio of Snappy and LZ4 compression algorithms
   public void benchmarkTestCompression() throws IOException
   {
      BenchmarkResult snappy;
      BenchmarkResult LZ4;
      snappy = benchmarkTestCompressionRatioSnappy();
      LZ4 = benchmarkTestCompressionRatioLZ4();

      System.out.println("Snappy compression ratio for random input: " + snappy.compressionRatioRandom * 100);
      System.out.println("Snappy compression ratio for hybrid input: " + snappy.compressionRatioHybrid * 100);
      System.out.println("Snappy compression ratio for repeating input: " + snappy.compressionRatioRepeat * 100);
      System.out.println("LZ4 compression ratio for random input: " + LZ4.compressionRatioRandom * 100);
      System.out.println("LZ4 compression ratio for random input: " + LZ4.compressionRatioHybrid * 100);
      System.out.println("LZ4 compression ratio for repeating input: " + LZ4.compressionRatioRandom * 100);
   }
   public BenchmarkResult benchmarkTestCompressionRatioSnappy() throws IOException
   {
      // Setup buffers for Snappy compression
      BenchmarkResult results = new BenchmarkResult();

      ByteBuffer random = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer hybrid =  ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer repeat = ByteBuffer.allocateDirect(ELEMENTS * 4);

      ByteBuffer randomOut = ByteBuffer.allocateDirect(SnappyUtils.maxCompressedLength(random.capacity()));
      ByteBuffer hybridOut = ByteBuffer.allocateDirect(SnappyUtils.maxCompressedLength(hybrid.capacity()));
      ByteBuffer repeatOut = ByteBuffer.allocateDirect(SnappyUtils.maxCompressedLength(repeat.capacity()));

      for (int i = 0; i < ELEMENTS; i++)
      {
         random.putInt(rand.nextInt());

         if (i % 20 < 10)
         {
            hybrid.putInt(12);
         }
         else
         {
            hybrid.putInt(rand.nextInt());
         }

         repeat.putInt(10);
      }

      random.flip();
      hybrid.flip();
      repeat.flip();

      SnappyUtils.compress(random, randomOut);
      SnappyUtils.compress(hybrid, hybridOut);
      SnappyUtils.compress(repeat, repeatOut);

      randomOut.flip();
      hybridOut.flip();
      repeatOut.flip();

      results.compressionRatioRandom = (double) randomOut.limit() / random.limit();
      results.compressionRatioHybrid = (double) hybridOut.limit() / hybrid.limit();
      results.compressionRatioRepeat = (double) repeatOut.limit() / repeat.limit();

      return results;
   }

   public BenchmarkResult benchmarkTestCompressionRatioLZ4()
   {
      // Setup buffers and variable for Lz4 compression
      BenchmarkResult results = new BenchmarkResult();
      LZ4CompressionImplementation impl = new LZ4CompressionImplementation();

      ByteBuffer random = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer hybrid = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer repeat = ByteBuffer.allocateDirect(ELEMENTS * 4);

      ByteBuffer randomOut = ByteBuffer.allocateDirect(impl.maxCompressedLength(random.capacity()));
      ByteBuffer hybridOut = ByteBuffer.allocateDirect(impl.maxCompressedLength(hybrid.capacity()));
      ByteBuffer repeatOut = ByteBuffer.allocateDirect(impl.maxCompressedLength(repeat.capacity()));

      random.position(0);
      hybrid.position(0);
      repeat.position(0);

      for (int i = 0; i < ELEMENTS; i++)
      {
         random.putInt(rand.nextInt());

         if ( i % 20 < 10)
         {
            hybrid.putInt(12);
         }
         else
         {
            hybrid.putInt(rand.nextInt());
         }

         repeat.putInt(10);
      }

      random.flip();
      hybrid.flip();
      repeat.flip();

      random.position(0);
      hybrid.position(0);
      repeat.position(0);

      impl.compress(random, randomOut);
      impl.compress(hybrid, hybridOut);
      impl.compress(repeat, repeatOut);

      randomOut.flip();
      hybridOut.flip();
      repeatOut.flip();

      results.compressionRatioRandom = (double) randomOut.limit() / random.limit();
      results.compressionRatioHybrid = (double) hybridOut.limit() / hybrid.limit();
      results.compressionRatioRepeat = (double) repeatOut.limit() / repeat.limit();

      return results;
   }

   @Test
   // This test is meant to return the run times of the Snappy and LZ4 compression algorithms
   public void benchmarkTestForTime() throws IOException
   {
      BenchmarkResult snappy;
      BenchmarkResult LZ4;

      snappy = benchmarkTimeSnappy();
      LZ4 = benchmarkTimeLZ4();

      String snappyType = COMPRESS_ONLY ? "Compressed " : (DECOMPRESS_ONLY ? "Decompressed " : "Compress and Decompress ");
      String LZ4Type = COMPRESS_ONLY ? "Compressed " : (DECOMPRESS_ONLY ? "Decompressed " : "Compress and Decompress ");

      assertTrue(snappy.compressionTime > LZ4.compressionTime);

      System.out.println(snappyType + "Snappy Time: " + snappy.compressionTime + ", for element size: " + ELEMENTS);
      System.out.println(LZ4Type + "LZ4 Time: " + LZ4.compressionTime + ", for element size: " + ELEMENTS);
   }


   public BenchmarkResult benchmarkTimeSnappy() throws IOException
   {
      // Setup buffers and variable for Snappy compression
      BenchmarkResult results = new BenchmarkResult();
      Stopwatch stopwatch = new Stopwatch();
      int inOffset = rand.nextInt(ELEMENTS);
      int outOffset = rand.nextInt(ELEMENTS);
      int decompressOffset = rand.nextInt(ELEMENTS);
      double time;

      ByteBuffer in = ByteBuffer.allocate(ELEMENTS * 4 + inOffset);
      ByteBuffer out = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(in.remaining()) + outOffset);
      ByteBuffer decompress = ByteBuffer.allocateDirect(ELEMENTS * 4 + decompressOffset);

      in.position(inOffset);
      out.position(outOffset);
      decompress.position(decompressOffset);

      for (int i = 0; i < ELEMENTS; i++)
      {
         in.putInt(rand.nextInt());
      }

      in.flip();
      in.position(inOffset);

      if (COMPRESS_ONLY)
      {
         // Return the time strictly for compressing the data
         stopwatch.start();
         SnappyUtils.compress(in, out);
         time = stopwatch.totalElapsed();

         results.compressionTime = time;

         return results;
      }
      else if (DECOMPRESS_ONLY)
      {
         SnappyUtils.compress(in, out);

         out.flip();
         out.position(outOffset);

         // Return the time strictly for decompressing the data
         stopwatch.start();
         SnappyUtils.uncompress(out, decompress);
         time = stopwatch.totalElapsed();

         results.compressionTime = time;

         return results;
      }
      else
      {
         // Return time for compressing and decompressing the data
         stopwatch.start();
         SnappyUtils.compress(in, out);
         assertEquals(0, in.remaining());

         out.flip();
         out.position(outOffset);

         SnappyUtils.uncompress(out, decompress);
         time = stopwatch.totalElapsed();

         in.position(inOffset);
         decompress.flip();
         decompress.position(decompressOffset);
         assertEquals(ELEMENTS * 4, decompress.remaining());

         for (int i = 0; i < ELEMENTS; i++)
         {
            Assertions.assertEquals(in.getInt(), decompress.getInt());
         }

         results.compressionTime = time;

         return results;
      }
   }


   public BenchmarkResult benchmarkTimeLZ4()
   {
      // Setup buffers and variable for Lz4 compression
      LZ4CompressionImplementation impl = new LZ4CompressionImplementation();
      BenchmarkResult results = new BenchmarkResult();

      Stopwatch stopwatch = new Stopwatch();
      double time;

      ByteBuffer in = ByteBuffer.allocateDirect(ELEMENTS * 4);
      ByteBuffer out = ByteBuffer.allocateDirect(impl.maxCompressedLength(in.capacity()));
      ByteBuffer decompress = ByteBuffer.allocateDirect(impl.minimumDecompressedLength(out.capacity()));

      in.position(0);
      out.position(0);

      for (int i = 0; i < ELEMENTS; i++)
      {
         in.putInt(rand.nextInt());
      }

      in.flip();
      in.position(0);

      if (COMPRESS_ONLY)
      {
         // Return the time strictly for compressing the data
         stopwatch.start();
         impl.compress(in, out);
         time = stopwatch.totalElapsed();

         results.compressionTime = time;

         return results;
      }
      else if (DECOMPRESS_ONLY)
      {
         // Return the time strictly for decompressing the data
         impl.compress(in, out);

         out.flip();
         out.position(0);

         stopwatch.start();
         impl.decompress(out, decompress, decompress.limit());
         time = stopwatch.totalElapsed();

         results.compressionTime = time;

         return results;
      }
      else
      {
         // Return time for compressing and decompressing the data
         stopwatch.start();
         impl.compress(in, out);

         out.flip();
         out.position(0);

         impl.decompress(out, decompress, decompress.limit());
         time = stopwatch.totalElapsed();

         assertEquals(0, in.remaining());
         Assertions.assertEquals(in, decompress);

         results.compressionTime = time;

         return results;
      }
   }
}
