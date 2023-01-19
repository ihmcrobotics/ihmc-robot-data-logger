package us.ihmc.tools.compression;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.DoubleSupplier;

import static org.jcodec.common.Assert.assertTrue;

/**
 * These tests are meant to replicate the CompressionBenchmark.java but with generated data since its much faster to load.
 * With the idea that the ratios should match between the CompressionBenchmark.java and this.
 */

//TODO Currently the ratios don't match because CompressionBenchmark.java calculates it in a different way, need to get them to match

public class LZ4CompressorTypesTest
{
   public static final int ELEMENTS = 30197;
   private final Random random = new Random(1234);
   private ByteBuffer[] dataset;
   private ByteBuffer[] datasetDirect;
   private final double totalSize = (ELEMENTS * 4) * 12;

   public void fillRandomData()
   {
      dataset = new ByteBuffer[12];

      for (int i = 0; i < 12; i++)
      {
         dataset[i] = ByteBuffer.allocate(ELEMENTS * 4);
         for (int j = 0; j < ELEMENTS; j++)
         {
            dataset[i].putInt(random.nextInt());
         }
      }
   }

   public void fillRandomDataDirect()
   {
      datasetDirect = new ByteBuffer[12];

      for (int i = 0; i < 12; i++)
      {
         datasetDirect[i] = ByteBuffer.allocateDirect(ELEMENTS * 4);
         for (int j = 0; j < ELEMENTS; j++)
         {
            datasetDirect[i].putInt(random.nextInt());
         }
      }
   }

   public void warmup()
   {
      for (int i = 0; i < 400; i++)
      {
         testLZ4FactorySafeInstance();
         testLZ4FactoryUnsafeInstance();
         testLZ4FactoryNativeInstance();
         testLZ4FactorySafeInstanceDirect();
         testLZ4FactoryUnsafeInstanceDirect();
         testLZ4FactoryNativeInstanceDirect();
      }
   }

   public void benchmarkPrintFunction(String name, int elements, DoubleSupplier compressionRatio)
   {
      System.out.println("--- " + name + " ---");
      long start = System.nanoTime();
      double compressionFactor = compressionRatio.getAsDouble();
      long duration = System.nanoTime() - start;

      System.out.println("Compression Factor: " + compressionFactor);
      System.out.println("Duration :" + Conversions.nanosecondsToSeconds(duration) + "s");
      System.out.println("Time per data line: " + Conversions.nanosecondsToMilliseconds((double) (duration / elements)) + "ms");
      System.out.println();
   }

   @Test
   public void benchmarkLZ4()
   {
      fillRandomData();
      fillRandomDataDirect();

      // JIT warmup
      warmup();

      // Actual Benchmark
      benchmarkPrintFunction("LZ4 (Safe)", ELEMENTS, this::testLZ4FactorySafeInstance);
      benchmarkPrintFunction("LZ4 (Safe - Direct Buffer)", ELEMENTS, this::testLZ4FactorySafeInstanceDirect);
      benchmarkPrintFunction("LZ4 (Unsafe)", ELEMENTS, this::testLZ4FactoryUnsafeInstance);
      benchmarkPrintFunction("LZ4 (Unsafe - Direct Buffer)", ELEMENTS, this::testLZ4FactoryUnsafeInstanceDirect);
      benchmarkPrintFunction("LZ4 (JNI)", ELEMENTS, this::testLZ4FactoryNativeInstance);
      benchmarkPrintFunction("LZ4 (JNI - Direct Buffer))", ELEMENTS, this::testLZ4FactoryNativeInstanceDirect);
      benchmarkPrintFunction("Copy Elements", ELEMENTS, this::testCopy);
      benchmarkPrintFunction("Copy Elements (Direct)", ELEMENTS, this::testCopyDirect);
   }

   public double testLZ4FactorySafeInstance()
   {
      LZ4Compressor safeCompressor = LZ4Factory.safeInstance().fastCompressor();
      ByteBuffer target = ByteBuffer.allocate(safeCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4(safeCompressor, target);
   }

   public double testLZ4FactorySafeInstanceDirect()
   {
      LZ4Compressor safeCompressor = LZ4Factory.safeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(safeCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4Direct(safeCompressor, targetDirect);
   }

   public double testLZ4FactoryUnsafeInstance()
   {
      LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInstance().fastCompressor();
      ByteBuffer target = ByteBuffer.allocate(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4(unsafeCompressor, target);
   }

   public double testLZ4FactoryUnsafeInstanceDirect()
   {
      LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4Direct(unsafeCompressor, targetDirect);
   }

   public double testLZ4FactoryNativeInstance()
   {
      LZ4Compressor jniCompressor = LZ4Factory.nativeInstance().fastCompressor();
      ByteBuffer target = ByteBuffer.allocate(jniCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4(jniCompressor, target);
   }

   public double testLZ4FactoryNativeInstanceDirect()
   {
      LZ4Compressor jniCompressor = LZ4Factory.nativeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(jniCompressor.maxCompressedLength(ELEMENTS * 4));

      return compressLZ4Direct(jniCompressor, targetDirect);
   }

   public double testCopy()
   {
      ByteBuffer target = ByteBuffer.allocate(ELEMENTS * 4);

      double compressedSize = 0.0;

      for (ByteBuffer byteBuffer : dataset)
      {
         byteBuffer.clear();
         target.clear();
         target.put(byteBuffer);
         compressedSize += target.position();
      }
      return compressedSize / totalSize;
   }

   public double testCopyDirect()
   {
      ByteBuffer target = ByteBuffer.allocateDirect(ELEMENTS * 4);

      double compressedSize = 0.0;

      for (ByteBuffer byteBuffer : datasetDirect)
      {
         byteBuffer.clear();
         target.clear();
         target.put(byteBuffer);
         compressedSize += target.position();
      }

      return compressedSize / totalSize;
   }

   public double compressLZ4(LZ4Compressor compressor, ByteBuffer target)
   {
      int compressedSize = 0;

      for (ByteBuffer byteBuffer : dataset)
      {
         byteBuffer.clear();
         target.clear();

         compressor.compress(byteBuffer, target);
         compressedSize += target.position();
         assertDataNotEqualTarget(byteBuffer, target);
      }

      return compressedSize / totalSize;
   }

   public double compressLZ4Direct(LZ4Compressor compressor, ByteBuffer target)
   {
      int compressedSize = 0;

      for (ByteBuffer byteBuffer : dataset)
      {
         byteBuffer.clear();
         target.clear();

         compressor.compress(byteBuffer, target);
         compressedSize += target.position();
         assertDataNotEqualTarget(byteBuffer, target);
      }

      return compressedSize / totalSize;
   }

   public void assertDataNotEqualTarget(ByteBuffer data, ByteBuffer target)
   {
      double totalLength = target.capacity();
      double matchingCount = 0.0;
      double maxRatioOfMatchingData = 0.1;
      double actualRatioOfMatchingData;

      for (int i = 0; i < data.capacity(); i++)
      {
         if (data.get(i) == target.get(i))
         {
            matchingCount += 1;
         }
      }

      actualRatioOfMatchingData = matchingCount / totalLength;
      assertTrue(maxRatioOfMatchingData > actualRatioOfMatchingData);
   }
}
