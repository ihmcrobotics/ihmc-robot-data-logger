package us.ihmc.robotDataLogger.util;

import logger_msgs.Handshake;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HandshakeHashCalculator
{
   public static String calculateHash(Handshake handshake)
   {
      try
      {
         // Create a CDRBuffer to serialize the handshake
         CDRBuffer buffer = new CDRBuffer();
         buffer.ensureRemainingCapacity(handshake.calculateSizeBytes(0) + 4); // +4 for payload header

         // Write payload header and serialize the handshake
         buffer.writePayloadHeader();
         handshake.serialize(buffer);

         // Get the underlying ByteBuffer
         ByteBuffer byteBuffer = buffer.getBufferUnsafe();

         // Hash the serialized data
         MessageDigest md = MessageDigest.getInstance("SHA-256");

         // Position and limit the buffer correctly
         byteBuffer.flip();
         md.update(byteBuffer);

         return Base64.getEncoder().encodeToString(md.digest());
      }
      catch (NoSuchAlgorithmException e)
      {
         throw new RuntimeException(e);
      }
   }
}
