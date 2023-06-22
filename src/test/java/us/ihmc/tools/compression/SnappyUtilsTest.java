package us.ihmc.tools.compression;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SnappyUtilsTest
{
   private final Random rand = new Random(98753244356L);

   @Test
   // Checks to ensure that if the compressed data buffer is to small that an error will be thrown and nothing will be compressed
   public void testIllegalArgumentException()
   {
      int elements = 1024;

      ByteBuffer data = ByteBuffer.allocate(elements);

      // This will cause the intended error because the size of the compressed data is too small, should throw exception
      ByteBuffer dataCompressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(data.remaining()) - 1);

      for (int i = 0; i < 24; i++)
      {
         for (int j = 0; j < elements / 4; j++)
         {
            data.putInt(rand.nextInt());
         }

         data.flip();

         Throwable thrown = assertThrows(IllegalArgumentException.class, () -> SnappyUtils.compress(data, dataCompressed));
         assertEquals("Cannot compress to output buffer, buffer size is: 1225, need 1226", thrown.getMessage());
      }
   }

   @Test
   // Uses ByteBuffer.allocateDirect for all the buffers and tests compression with it
   public void testDirectByteBuffers() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocateDirect(elements * 4 + dataOffset);
      ByteBuffer compressed = ByteBuffer.allocateDirect(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset);
      ByteBuffer decompress = ByteBuffer.allocateDirect(elements * 4 + decompressOffset);

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   // Uses ByteBuffer.allocateDirect for the original data buffer, but not the rest
   public void testDirectData() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocateDirect(elements * 4 + dataOffset);
      ByteBuffer compressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset);
      ByteBuffer decompress = ByteBuffer.allocate(elements * 4 + decompressOffset);

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   public void testDirectCompressed() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocate(elements * 4 + dataOffset);
      ByteBuffer compressed = ByteBuffer.allocateDirect(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset);
      ByteBuffer decompress = ByteBuffer.allocate(elements * 4 + decompressOffset);

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   // Uses ByteBuffer.allocateDirect for the decompressed values but not for the other two buffers
   public void testDirectDecompressed() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocate(elements * 4 + dataOffset);
      ByteBuffer compressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset);
      ByteBuffer decompress = ByteBuffer.allocateDirect(elements * 4 + decompressOffset);

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   // For all the buffers this test just uses ByteBuffer.allocate() and checks to see if that compresses/decompresses correctly
   public void testAllocateHeap() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocate(elements * 4 + dataOffset);
      ByteBuffer compressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset);
      ByteBuffer decompress = ByteBuffer.allocate(elements * 4 + decompressOffset);

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   public void testSliceBuffers() throws IOException
   {
      int elements = 128 + rand.nextInt(128);
      int dataOffset = rand.nextInt(128);
      int compressedOffset = rand.nextInt(128);
      int decompressOffset = rand.nextInt(128);

      int inSlice = rand.nextInt(128);
      int compressedSlice = rand.nextInt(128);
      int decompressSlice = rand.nextInt(128);

      ByteBuffer data = ByteBuffer.allocate(elements * 4 + dataOffset + inSlice);
      ByteBuffer compressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(data.remaining()) + compressedOffset + compressedSlice);
      ByteBuffer decompress = ByteBuffer.allocate(elements * 4 + decompressOffset + decompressSlice);

      data.position(inSlice);
      data = data.slice();
      compressed.position(compressedSlice);
      compressed = compressed.slice();
      decompress.position(decompressSlice);
      decompress = decompress.slice();

      testCompression(elements, data, dataOffset, compressed, compressedOffset, decompress, decompressOffset);
   }

   @Test
   // Checks the compression ratio between random values and repetitive values, and asserts that the repetitive data results data a smaller buffer
   public void testCompressionRatio() throws IOException
   {
      int elements = 1024;
      ByteBuffer random = ByteBuffer.allocate(elements);
      ByteBuffer tens = ByteBuffer.allocate(elements);

      ByteBuffer randomCompressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(elements));
      ByteBuffer tensCompressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(elements));

      for (int i = 0; i < elements / 4; i++)
      {
         random.putInt(rand.nextInt());
         tens.putInt(10);
      }
      random.flip();
      tens.flip();

      // Snappy Compression is good at compacting repetitive data into a very small compressed size, that is what this test is checking
      SnappyUtils.compress(random, randomCompressed);
      SnappyUtils.compress(tens, tensCompressed);

      assertTrue(tensCompressed.position() < randomCompressed.position());
   }

   // This method is where the actual compression is happening, uses data all the tests to actually compress and decompress the data and checks for equality
   private void testCompression(int elements, ByteBuffer data, int dataOffset, ByteBuffer compressed, int compressedOffset, ByteBuffer decompress, int decompressOffset)
         throws IOException
   {
      data.position(dataOffset);
      compressed.position(compressedOffset);
      decompress.position(decompressOffset);

      // Fills the original data buffer with random values
      for (int i = 0; i < elements; i++)
      {
         data.putInt(rand.nextInt());
      }

      // The use of ByteBuffer.flip() and ByteBuffer.position() ensure the buffer is data the correct state to be compressed
      data.flip();
      data.position(dataOffset);

      SnappyUtils.compress(data, compressed);
      assertEquals(0, data.remaining());

      // The use of ByteBuffer.flip() and ByteBuffer.position() ensure the buffer is data the correct state to be decompressed
      compressed.flip();
      compressed.position(compressedOffset);

      SnappyUtils.uncompress(compressed, decompress);

      data.position(dataOffset);
      decompress.flip();
      decompress.position(decompressOffset);

      // Check to make sure the decompressed data matches the original data, otherwise something failed
      assertEquals(elements * 4, decompress.remaining());
      for (int i = 0; i < elements; i++)
      {
         assertEquals(data.getInt(), decompress.getInt());
      }
   }
}
