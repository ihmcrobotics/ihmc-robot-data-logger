package us.ihmc.tools.compression;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.bytedeco.javacpp.*;
import org.bytedeco.lz4.*;
import org.bytedeco.lz4.global.lz4;

public class LZ4BytedecoCompressionImplementation
{
   public static LZ4FDecompressionContext ByteDecoLZ4CompressionImplementation() throws LZ4Exception
   {
            final LZ4FDecompressionContext dctx = new LZ4FDecompressionContext();
            final long ctxError = lz4.LZ4F_createDecompressionContext(dctx, lz4.LZ4F_VERSION);
            checkForError(ctxError);
            return dctx;
   }

   public static double compress(ByteBuffer src, Pointer srcPointer, ByteBuffer dst, Pointer dstPointer)
   {
      return lz4.LZ4F_compressFrame(dstPointer, dst.limit(), srcPointer, src.limit(), null);
   }

   public static void decompress(LZ4FDecompressionContext dctx,
                                 Pointer compressedPointer,
                                 Pointer dstPointer,
                                 SizeTPointer srcSize,
                                 SizeTPointer dstSize,
                                 ByteBuffer uncompressed,
                                 int uncompressedSize)
   {
      if (uncompressed.position() + uncompressedSize > uncompressed.limit())
      {
         throw new BufferOverflowException();
      }

      lz4.LZ4F_decompress(dctx, dstPointer, dstSize, compressedPointer, srcSize, null);
   }

   public int maxCompressedLength(int uncompressedLength)
   {
      final int maxCompressedSize = (int) lz4.LZ4F_compressFrameBound(uncompressedLength, null);
      return lz4.LZ4_compressBound(maxCompressedSize);
   }

   public int minimumDecompressedLength(int compressedLength)
   {
      double y = (long) (compressedLength - 16) * 255;
      return (int) Math.round(y / 256);
   }

   private static void checkForError(long errorCode) throws LZ4Exception {
      if (lz4.LZ4F_isError(errorCode) != 0) {
         throw new LZ4Exception(lz4.LZ4F_getErrorName(errorCode).getString());
      }
   }

   public static final class LZ4Exception extends Exception {
      public LZ4Exception(final String message) {
         super(message);
      }
   }
}