package us.ihmc.tools.compression;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.DoubleSupplier;

public class LZ4CompressorTypesBenchmarkTest
{
   int ELEMENTS = 1024;
   Random random = new Random(1234);
   ByteBuffer[] dataset;
   ByteBuffer[] datasetDirect;
   double totalSize = 0;

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

         totalSize += (ELEMENTS * 4);
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

         totalSize += (ELEMENTS * 4);
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

      //JIT warmup
      warmup();

      //Actual Benchmark
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

      double compressedSize = compressLZ4(safeCompressor, target);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testLZ4FactorySafeInstanceDirect()
   {
      LZ4Compressor safeCompressor = LZ4Factory.safeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(safeCompressor.maxCompressedLength(ELEMENTS * 4));

      double compressedSize = compressLZ4Direct(safeCompressor, targetDirect);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testLZ4FactoryUnsafeInstance()
   {
      LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInstance().fastCompressor();
      ByteBuffer target = ByteBuffer.allocate(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));

      double compressedSize = compressLZ4(unsafeCompressor, target);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testLZ4FactoryUnsafeInstanceDirect()
   {
      LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(unsafeCompressor.maxCompressedLength(ELEMENTS * 4));

      double compressedSize = compressLZ4Direct(unsafeCompressor, targetDirect);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testLZ4FactoryNativeInstance()
   {
      LZ4Compressor jniCompressor = LZ4Factory.nativeInstance().fastCompressor();
      ByteBuffer target = ByteBuffer.allocate(jniCompressor.maxCompressedLength(ELEMENTS * 4));

      double compressedSize = compressLZ4(jniCompressor, target);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testLZ4FactoryNativeInstanceDirect()
   {
      LZ4Compressor jniCompressor = LZ4Factory.nativeInstance().fastCompressor();
      ByteBuffer targetDirect = ByteBuffer.allocateDirect(jniCompressor.maxCompressedLength(ELEMENTS * 4));

      double compressedSize = compressLZ4Direct(jniCompressor, targetDirect);

      return compressedSize / (ELEMENTS * 4);
   }

   public double testCopy()
   {
      ByteBuffer target = ByteBuffer.allocate(ELEMENTS * 4);

      double compressedSize = 0;

      for( int i = 0; i < dataset.length; i++)
      {
         dataset[i].clear();
         target.clear();
         target.put(dataset[i]);
         compressedSize += target.position();
      }
      return compressedSize / totalSize;
   }

   public double testCopyDirect()
   {
      ByteBuffer target = ByteBuffer.allocateDirect(ELEMENTS * 4);

      double compressedSize = 0;

      for( int i = 0; i < datasetDirect.length; i++)
      {
         datasetDirect[i].clear();
         target.clear();
         target.put(datasetDirect[i]);
         compressedSize += target.position();
      }

      return compressedSize / totalSize;
   }

   public double compressLZ4(LZ4Compressor compressor, ByteBuffer target)
   {
      int compressedSize = 0;

      for (int i = 0; i < dataset.length; i++)
      {
         dataset[i].clear();
         target.clear();

         compressor.compress(dataset[i], target);
         compressedSize += target.position();
         checkDataIsDifferent(dataset[i], target);
      }

      return compressedSize / totalSize;
   }

   public double compressLZ4Direct(LZ4Compressor compressor, ByteBuffer target)
   {
      int compressedSize = 0;

      for (int i = 0; i < datasetDirect.length; i++)
      {
         dataset[i].clear();
         target.clear();

         compressor.compress(dataset[i], target);
         compressedSize += target.position();
         checkDataIsDifferent(dataset[i], target);
      }

      return compressedSize / totalSize;
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
}
