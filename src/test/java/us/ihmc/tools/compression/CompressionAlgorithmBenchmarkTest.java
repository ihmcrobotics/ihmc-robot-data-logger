package us.ihmc.tools.compression;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.time.Stopwatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

import static org.jcodec.common.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

public class CompressionAlgorithmBenchmarkTest
{
   private static final Random rand = new Random(98753244356L);

   private final boolean COMPRESS_ONLY = false;
   private final boolean DECOMPRESS_ONLY = false;
   private final int ELEMENTS = 128000000;

   @Test
   // This test is meant to return the run times of the Snappy and LZ4 compression algorithms
   public void benchmarkTimeCompression() throws IOException
   {
      ArrayList<Double> snappy;
      ArrayList<Double> LZ4;

      snappy = benchmarkTimeSnappy();
      LZ4 = benchmarkTimeLZ4();

      String snappyType = COMPRESS_ONLY ? "Compressed " : (DECOMPRESS_ONLY ? "Decompressed " : "Compress and Decompress ");
      String LZ4Type = COMPRESS_ONLY ? "Compressed " : (DECOMPRESS_ONLY ? "Decompressed " : "Compress and Decompress ");

      assertTrue(snappy.get(0) > LZ4.get(0));

      System.out.println(snappyType + "Snappy Time: " + snappy.get(0) + ", for element size: " + snappy.get(1));
      System.out.println(LZ4Type + "LZ4 Time: " + LZ4.get(0) + ", for element size: " + LZ4.get(1));
   }


   public ArrayList<Double> benchmarkTimeSnappy() throws IOException
   {
      // Setup buffers and variable for Snappy compression
      ArrayList<Double> results = new ArrayList<>();
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

         results.add(time);
         results.add((double) ELEMENTS);
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

         results.add(time);
         results.add((double) ELEMENTS);
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

         results.add(time);
         results.add((double) ELEMENTS);
         return results;
      }
   }


   public ArrayList<Double> benchmarkTimeLZ4()
   {
      // Setup buffers and variable for Lz4 compression
      LZ4CompressionImplementation impl = new LZ4CompressionImplementation();
      ArrayList<Double> results = new ArrayList<>();

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

         results.add(time);
         results.add((double) ELEMENTS);
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

         results.add(time);
         results.add((double) ELEMENTS);
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

         results.add(time);
         results.add((double) ELEMENTS);
         return results;
      }
   }
}
