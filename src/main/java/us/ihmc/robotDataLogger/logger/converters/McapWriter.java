package us.ihmc.robotDataLogger.logger.converters;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.luben.zstd.Zstd;

/**
 * Writes a chunked, indexed MCAP 1.0 file with JSON message encoding and zstd chunk compression.
 * <p>
 * Layout:
 * <pre>
 *   [magic] [Header] [Schema×N] [Channel×N]
 *   [Chunk] [Chunk] ...          ← messages buffered into 4 MB chunks, zstd-compressed
 *   [DataEnd]
 *   ── summary section ──────────────────────
 *   [Schema×N] [Channel×N]       ← duplicated so Foxglove can build its lookup table
 *   [ChunkIndex×N]
 *   [Statistics]
 *   [SummaryOffset×2]
 *   [Footer] [magic]
 * </pre>
 * See https://mcap.dev/spec for the format specification.
 */
class McapWriter implements Closeable
{
   private static final byte[] MAGIC = {(byte) 0x89, 0x4D, 0x43, 0x41, 0x50, 0x30, 0x0D, 0x0A};

   private static final byte OP_HEADER          = 0x01;
   private static final byte OP_FOOTER          = 0x02;
   private static final byte OP_SCHEMA          = 0x03;
   private static final byte OP_CHANNEL         = 0x04;
   private static final byte OP_MESSAGE         = 0x05;
   private static final byte OP_CHUNK           = 0x06;
   private static final byte OP_MESSAGE_INDEX   = 0x07;
   private static final byte OP_CHUNK_INDEX     = 0x08;
   private static final byte OP_STATISTICS      = 0x0B;
   private static final byte OP_SUMMARY_OFFSET  = 0x0E;
   private static final byte OP_DATA_END        = 0x0F;

   /** Flush a chunk once its uncompressed buffer reaches this size. */
   private static final int CHUNK_TARGET_BYTES = 4 * 1024 * 1024; // 4 MB

   private final OutputStream out;
   private long bytesWritten = 0;

   // Minimal Schema/Channel records for the summary section.
   // Full schemas (with data) are written once to the data section so Foxglove can decode messages.
   // The summary copies carry only id/name/encoding (empty data) so they are small enough for
   // Foxglove's summary parser to handle, while still satisfying its indexed-file detection.
   private final List<byte[]> summarySchemaRecords  = new ArrayList<>();
   private final List<byte[]> summaryChannelRecords = new ArrayList<>();

   // Current chunk accumulation
   private final ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream(CHUNK_TARGET_BYTES + 65536);
   private long chunkStartTime = Long.MAX_VALUE;
   private long chunkEndTime   = 0;

   private static class ChunkInfo
   {
      long startTime, endTime, fileOffset, recordLength, compressedSize, uncompressedSize;
      long messageIndexLength;
      Map<Integer, Long> messageIndexOffsets = new LinkedHashMap<>();
   }
   private final List<ChunkInfo> chunks = new ArrayList<>();

   // Per-chunk message tracking: channel_id → list of [logTimeNs, chunkBufferOffset] pairs
   private final Map<Integer, List<long[]>> chunkMessageEntries = new LinkedHashMap<>();

   // Statistics
   private long messageCount     = 0;
   private int  schemaCount      = 0;
   private int  channelCount     = 0;
   private long messageStartTime = Long.MAX_VALUE;
   private long messageEndTime   = 0;
   private final Map<Integer, Long> channelMessageCounts = new HashMap<>();

   McapWriter(OutputStream output) throws IOException
   {
      this.out = new BufferedOutputStream(output, 1 << 20);
      writeMagic();
      writeHeader();
   }

   void addSchema(int id, String name, String encoding, byte[] schemaData) throws IOException
   {
      byte[] nameBytes     = prefixedString(name);
      byte[] encodingBytes = prefixedString(encoding);
      int size = 2 + nameBytes.length + encodingBytes.length + 4 + schemaData.length;
      ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
      buf.putShort((short) id);
      buf.put(nameBytes);
      buf.put(encodingBytes);
      buf.putInt(schemaData.length);
      buf.put(schemaData);
      byte[] recordData = buf.array();
      writeRecord(OP_SCHEMA, recordData);

      // Minimal version for the summary: same id/name/encoding but zero data bytes.
      int minLen = 2 + nameBytes.length + encodingBytes.length + 4; // 4 = uint32 data-length field
      ByteBuffer min = ByteBuffer.allocate(minLen).order(ByteOrder.LITTLE_ENDIAN);
      min.putShort((short) id);
      min.put(nameBytes);
      min.put(encodingBytes);
      min.putInt(0);
      summarySchemaRecords.add(min.array());

      schemaCount++;
   }

   void addChannel(int id, int schemaId, String topic, String messageEncoding, Map<String, String> metadata)
         throws IOException
   {
      byte[] topicBytes    = prefixedString(topic);
      byte[] encodingBytes = prefixedString(messageEncoding);
      byte[] metaBytes     = prefixedMap(metadata);
      int size = 2 + 2 + topicBytes.length + encodingBytes.length + metaBytes.length;
      ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
      buf.putShort((short) id);
      buf.putShort((short) schemaId);
      buf.put(topicBytes);
      buf.put(encodingBytes);
      buf.put(metaBytes);
      byte[] recordData = buf.array();
      writeRecord(OP_CHANNEL, recordData);
      summaryChannelRecords.add(recordData); // Channel records are small; re-emit as-is in summary
      channelCount++;
      channelMessageCounts.put(id, 0L);
   }

   void writeMessage(int channelId, long logTimeNs, long publishTimeNs, byte[] data) throws IOException
   {
      long sequence = channelMessageCounts.getOrDefault(channelId, 0L);
      ByteBuffer buf = ByteBuffer.allocate(2 + 4 + 8 + 8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
      buf.putShort((short) channelId);
      buf.putInt((int) (sequence & 0xFFFFFFFFL));
      buf.putLong(logTimeNs);
      buf.putLong(publishTimeNs);
      buf.put(data);
      int chunkOffset = chunkBuffer.size(); // byte offset of this record within the uncompressed chunk
      appendRecordToBuffer(chunkBuffer, OP_MESSAGE, buf.array());
      chunkMessageEntries.computeIfAbsent(channelId, k -> new ArrayList<>())
                         .add(new long[]{logTimeNs, chunkOffset});

      if (logTimeNs < chunkStartTime) chunkStartTime = logTimeNs;
      if (logTimeNs > chunkEndTime)   chunkEndTime   = logTimeNs;

      messageCount++;
      channelMessageCounts.merge(channelId, 1L, Long::sum);
      if (logTimeNs < messageStartTime) messageStartTime = logTimeNs;
      if (logTimeNs > messageEndTime)   messageEndTime   = logTimeNs;

      if (chunkBuffer.size() >= CHUNK_TARGET_BYTES)
         flushChunk();
   }

   @Override
   public void close() throws IOException
   {
      flushChunk();

      writeRecord(OP_DATA_END, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array());

      long summaryStart = bytesWritten;

      long schemaStart = bytesWritten;
      for (byte[] r : summarySchemaRecords)  writeRecord(OP_SCHEMA,  r);
      long schemaEnd = bytesWritten;

      long channelStart = bytesWritten;
      for (byte[] r : summaryChannelRecords) writeRecord(OP_CHANNEL, r);
      long channelEnd = bytesWritten;

      // ChunkIndex records
      long chunkIndexStart = bytesWritten;
      for (ChunkInfo ci : chunks)
         writeRecord(OP_CHUNK_INDEX, buildChunkIndex(ci));
      long chunkIndexEnd = bytesWritten;

      // Statistics
      long statsStart = bytesWritten;
      writeRecord(OP_STATISTICS, buildStatistics());
      long statsEnd = bytesWritten;

      // SummaryOffset records must appear last in the summary section.
      // These are required so Foxglove can locate each record group without scanning.
      writeRecord(OP_SUMMARY_OFFSET, buildSummaryOffset(OP_SCHEMA,   schemaStart,     schemaEnd     - schemaStart));
      writeRecord(OP_SUMMARY_OFFSET, buildSummaryOffset(OP_CHANNEL,  channelStart,    channelEnd    - channelStart));
      if (!chunks.isEmpty())
         writeRecord(OP_SUMMARY_OFFSET, buildSummaryOffset(OP_CHUNK_INDEX, chunkIndexStart, chunkIndexEnd - chunkIndexStart));
      writeRecord(OP_SUMMARY_OFFSET, buildSummaryOffset(OP_STATISTICS, statsStart, statsEnd - statsStart));

      long summaryOffsetStart = statsEnd; // file offset of the first SummaryOffset record

      ByteBuffer footer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
      footer.putLong(summaryStart);
      footer.putLong(summaryOffsetStart);
      footer.putInt(0); // summary_crc: 0 = not computed
      writeRecord(OP_FOOTER, footer.array());

      writeMagic();
      out.flush();
      out.close();
   }

   // ── Chunk management ─────────────────────────────────────────────────────────

   private void flushChunk() throws IOException
   {
      if (chunkBuffer.size() == 0) return;

      byte[] uncompressed   = chunkBuffer.toByteArray();
      byte[] compressed     = Zstd.compress(uncompressed);
      byte[] compressionStr = prefixedString("zstd");

      int    headerSize  = 8 + 8 + 8 + 4 + compressionStr.length + 8; // +8 for compressed_size field
      byte[] chunkRecord = new byte[headerSize + compressed.length];
      ByteBuffer buf = ByteBuffer.wrap(chunkRecord).order(ByteOrder.LITTLE_ENDIAN);
      buf.putLong(chunkStartTime);
      buf.putLong(chunkEndTime);
      buf.putLong(uncompressed.length); // uncompressed_size
      buf.putInt(0);                     // uncompressed_crc: 0 = not computed
      buf.put(compressionStr);
      buf.putLong(compressed.length);   // compressed_size (required by MCAP spec)
      buf.put(compressed);

      long fileOffset = bytesWritten;
      writeRecord(OP_CHUNK, chunkRecord);

      ChunkInfo ci = new ChunkInfo();
      ci.startTime        = chunkStartTime;
      ci.endTime          = chunkEndTime;
      ci.fileOffset       = fileOffset;
      ci.recordLength     = bytesWritten - fileOffset;
      ci.compressedSize   = compressed.length;
      ci.uncompressedSize = uncompressed.length;

      // Write one MessageIndex record per channel that had messages in this chunk.
      long msgIdxStart = bytesWritten;
      for (Map.Entry<Integer, List<long[]>> entry : chunkMessageEntries.entrySet())
      {
         int          chId       = entry.getKey();
         List<long[]> entries    = entry.getValue();
         int          entryBytes = entries.size() * 16; // each entry: uint64 log_time + uint64 offset
         ByteBuffer   miBuf      = ByteBuffer.allocate(2 + 4 + entryBytes).order(ByteOrder.LITTLE_ENDIAN);
         miBuf.putShort((short) chId);
         miBuf.putInt(entryBytes);
         for (long[] e : entries)
         {
            miBuf.putLong(e[0]); // log_time
            miBuf.putLong(e[1]); // offset within uncompressed chunk
         }
         ci.messageIndexOffsets.put(chId, bytesWritten);
         writeRecord(OP_MESSAGE_INDEX, miBuf.array());
      }
      ci.messageIndexLength = bytesWritten - msgIdxStart;

      chunks.add(ci);
      chunkMessageEntries.clear();
      chunkBuffer.reset();
      chunkStartTime = Long.MAX_VALUE;
      chunkEndTime   = 0;
   }

   // ── Record builders ──────────────────────────────────────────────────────────

   private void writeHeader() throws IOException
   {
      byte[] profile = prefixedString("");
      byte[] library = prefixedString("ihmc-robot-data-logger");
      ByteBuffer buf = ByteBuffer.allocate(profile.length + library.length);
      buf.put(profile);
      buf.put(library);
      writeRecord(OP_HEADER, buf.array());
   }

   private static byte[] buildChunkIndex(ChunkInfo ci)
   {
      byte[] compressionStr  = prefixedString("zstd");
      int    msgIdxMapBytes  = ci.messageIndexOffsets.size() * 10; // uint16 + uint64 per entry
      int    size            = 8 + 8 + 8 + 8 + 4 + msgIdxMapBytes + 8 + compressionStr.length + 8 + 8;
      ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
      buf.putLong(ci.startTime);
      buf.putLong(ci.endTime);
      buf.putLong(ci.fileOffset);
      buf.putLong(ci.recordLength);
      buf.putInt(msgIdxMapBytes);     // message_index_offsets: byte count
      for (Map.Entry<Integer, Long> e : ci.messageIndexOffsets.entrySet())
      {
         buf.putShort(e.getKey().shortValue());
         buf.putLong(e.getValue());
      }
      buf.putLong(ci.messageIndexLength);
      buf.put(compressionStr);
      buf.putLong(ci.compressedSize);
      buf.putLong(ci.uncompressedSize);
      return buf.array();
   }

   private byte[] buildStatistics()
   {
      int countMapBytes = channelMessageCounts.size() * 10;
      ByteBuffer buf = ByteBuffer.allocate(8 + 2 + 4 + 4 + 4 + 4 + 8 + 8 + 4 + countMapBytes)
                                 .order(ByteOrder.LITTLE_ENDIAN);
      buf.putLong(messageCount);
      buf.putShort((short) schemaCount);
      buf.putInt(channelCount);
      buf.putInt(0); // attachment_count
      buf.putInt(0); // metadata_count
      buf.putInt(chunks.size()); // chunk_count
      buf.putLong(messageStartTime == Long.MAX_VALUE ? 0 : messageStartTime);
      buf.putLong(messageEndTime);
      buf.putInt(countMapBytes);
      for (Map.Entry<Integer, Long> e : channelMessageCounts.entrySet())
      {
         buf.putShort(e.getKey().shortValue());
         buf.putLong(e.getValue());
      }
      return buf.array();
   }

   private static byte[] buildSummaryOffset(byte groupOp, long groupStart, long groupLength)
   {
      return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
                       .put(groupOp).putLong(groupStart).putLong(groupLength).array();
   }

   // ── Output helpers ───────────────────────────────────────────────────────────

   private void writeMagic() throws IOException
   {
      out.write(MAGIC);
      bytesWritten += MAGIC.length;
   }

   private void writeRecord(byte op, byte[] data) throws IOException
   {
      out.write(op);
      out.write(toLongLE(data.length));
      out.write(data);
      bytesWritten += 1 + 8 + data.length;
   }

   private static void appendRecordToBuffer(ByteArrayOutputStream baos, byte op, byte[] data)
   {
      baos.write(op);
      baos.write(toLongLE(data.length), 0, 8);
      baos.write(data, 0, data.length);
   }

   private static byte[] toLongLE(long value)
   {
      return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
   }

   // ── MCAP encoding primitives ─────────────────────────────────────────────────

   private static byte[] prefixedString(String s)
   {
      byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
      return ByteBuffer.allocate(4 + bytes.length).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.length).put(bytes).array();
   }

   private static byte[] prefixedMap(Map<String, String> map)
   {
      int contentSize = 0;
      for (Map.Entry<String, String> e : map.entrySet())
      {
         contentSize += 4 + e.getKey().getBytes(StandardCharsets.UTF_8).length;
         contentSize += 4 + e.getValue().getBytes(StandardCharsets.UTF_8).length;
      }
      ByteBuffer buf = ByteBuffer.allocate(4 + contentSize).order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(contentSize);
      for (Map.Entry<String, String> e : map.entrySet())
      {
         buf.put(prefixedString(e.getKey()));
         buf.put(prefixedString(e.getValue()));
      }
      return buf.array();
   }
}
