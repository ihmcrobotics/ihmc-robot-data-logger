package us.ihmc.tools.compression;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.bytedeco.javacpp.*;
import org.bytedeco.lz4.*;
import org.bytedeco.lz4.global.lz4;

public class LZ4BytedecoCompressionImplementation
{
   final static LZ4FDecompressionContext lz4DecompressionContext = new LZ4FDecompressionContext();

   /** Method checks to see if anything goes wrong with the decompression of the data */
   public static LZ4FDecompressionContext BytedecoLZ4CompressionImplementation() throws LZ4Exception
   {
            final long contextError = lz4.LZ4F_createDecompressionContext(lz4DecompressionContext, lz4.LZ4F_VERSION);
            checkForError(contextError);
            return lz4DecompressionContext;
   }

   /** Compresses the data using pointers that point to the addresses of the ByteBuffers */
   public static double compress(ByteBuffer source, Pointer sourcePointer, ByteBuffer destination, Pointer destionationPointer)
   {
      return lz4.LZ4F_compressFrame(destionationPointer, destination.limit(), sourcePointer, source.limit(), null);
   }

   /** Decompresses the data using several pointers that point to the ByteBuffers */
   public static void decompress(LZ4FDecompressionContext decompressionContext,
                                 Pointer compressedPointer,
                                 Pointer destinationPointer,
                                 SizeTPointer compressSize,
                                 SizeTPointer destinationSize,
                                 ByteBuffer uncompressed,
                                 int uncompressedSize)
   {
      if (uncompressed.position() + uncompressedSize > uncompressed.limit())
      {
         throw new BufferOverflowException();
      }

      lz4.LZ4F_decompress(decompressionContext, destinationPointer, destinationSize, compressedPointer, compressSize, null);
   }

   /** Computes the amount of space required for the compress Buffer based on the size of the input data */
   public int maxCompressedLength(int uncompressedLength)
   {
      final int maxCompressedSize = (int) lz4.LZ4F_compressFrameBound(uncompressedLength, null);
      return lz4.LZ4_compressBound(maxCompressedSize);
   }

   /** Computes the amount of space required for the decompress Buffer bases on the size of the compressed data */
   public int minimumDecompressedLength(int compressedLength)
   {
      double y = (long) (compressedLength - 16) * 255;
      return (int) Math.round(y / 256);
   }

   /** Checks for errors with the decompress method and throw's an exception from LZ4Exception if one is found */
   private static void checkForError(long errorCode) throws LZ4Exception
   {
      if (lz4.LZ4F_isError(errorCode) != 0) {
         throw new LZ4Exception(lz4.LZ4F_getErrorName(errorCode).getString());
      }
   }

   public static final class LZ4Exception extends Exception
   {
      public LZ4Exception(final String message) {
         super(message);
      }
   }
}