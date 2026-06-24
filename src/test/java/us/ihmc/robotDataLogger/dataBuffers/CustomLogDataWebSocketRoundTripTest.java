package us.ihmc.robotDataLogger.dataBuffers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.robotDataLogger.handshake.YoVariableHandShakeBuilder;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoLong;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

public class CustomLogDataWebSocketRoundTripTest
{
   @Test
   public void testPublisherToSubscriberMatchesWebsocketServerPath() throws IOException
   {
      Random random = new Random(42L);
      int numberOfVariables = 64;
      int numberOfJointStates = 0;

      YoRegistry registry = new YoRegistry("test");
      for (int i = 0; i < numberOfVariables; i++)
      {
         YoLong yoLong = new YoLong("var" + i, registry);
         yoLong.set(random.nextLong());
      }

      RegistrySendBufferBuilder builder = new RegistrySendBufferBuilder(registry);
      new YoVariableHandShakeBuilder("root", 0.001).addRegistryBuffer(builder);
      RegistrySendBuffer sendBuffer = builder.newInstance();
      sendBuffer.updateBufferFromVariables(123456789L, 99L, numberOfVariables);

      CustomLogDataPublisherType publisherType = new CustomLogDataPublisherType(numberOfVariables, numberOfJointStates);
      CDRBuffer publisherBuffer = new CDRBuffer();
      int maxSize = publisherType.calculateSizeBytes(0) + 4;
      publisherBuffer.ensureRemainingCapacity(maxSize);
      publisherBuffer.getBufferUnsafe().limit(maxSize);
      publisherBuffer.rewind();
      publisherBuffer.writePayloadHeader();
      publisherType.serialize(sendBuffer, publisherBuffer);

      byte[] wireBytes = new byte[publisherBuffer.getBufferUnsafe().remaining()];
      publisherBuffer.getBufferUnsafe().get(wireBytes);

      RegistryReceiveBuffer receiveBuffer = new RegistryReceiveBuffer(System.nanoTime());
      CustomLogDataSubscriberType subscriberType = new CustomLogDataSubscriberType(numberOfVariables, numberOfJointStates);

      deserializeFromWebsocketFrame(wireBytes, subscriberType, receiveBuffer, true);

      assertEquals(99L, receiveBuffer.getUid());
      assertEquals(123456789L, receiveBuffer.getTimestamp());
      assertEquals(1, receiveBuffer.getRegistryID());
      assertEquals(numberOfVariables, receiveBuffer.getNumberOfVariables());
      assertEquals(numberOfJointStates, receiveBuffer.getJointStateCount());
   }

   @Test
   public void testMissingFlipFailsLikeBrokenLoggerClient() throws IOException
   {
      byte[] wireBytes = createMinimalWirePayload();

      RegistryReceiveBuffer receiveBuffer = new RegistryReceiveBuffer(System.nanoTime());
      CustomLogDataSubscriberType subscriberType = new CustomLogDataSubscriberType(4, 2);

      assertThrows(Exception.class, () -> deserializeFromWebsocketFrame(wireBytes, subscriberType, receiveBuffer, false));
   }

   private static byte[] createMinimalWirePayload() throws IOException
   {
      YoRegistry registry = new YoRegistry("minimal");
      new YoLong("a", registry);
      new YoLong("b", registry);
      new YoLong("c", registry);
      new YoLong("d", registry);

      RegistrySendBufferBuilder builder = new RegistrySendBufferBuilder(registry);
      new YoVariableHandShakeBuilder("root", 0.001).addRegistryBuffer(builder);
      RegistrySendBuffer sendBuffer = builder.newInstance();
      sendBuffer.updateBufferFromVariables(1L, 2L, 4);

      CustomLogDataPublisherType publisherType = new CustomLogDataPublisherType(4, 2);
      CDRBuffer publisherBuffer = new CDRBuffer();
      publisherBuffer.ensureRemainingCapacity(publisherType.calculateSizeBytes(0) + 4);
      publisherBuffer.rewind();
      publisherBuffer.writePayloadHeader();
      publisherType.serialize(sendBuffer, publisherBuffer);

      byte[] wireBytes = new byte[publisherBuffer.getBufferUnsafe().remaining()];
      publisherBuffer.getBufferUnsafe().get(wireBytes);
      return wireBytes;
   }

   private static void deserializeFromWebsocketFrame(byte[] wireBytes,
                                                       CustomLogDataSubscriberType subscriberType,
                                                       RegistryReceiveBuffer receiveBuffer,
                                                       boolean flipAfterRead) throws IOException
   {
      CDRBuffer cdrBuffer = new CDRBuffer();
      cdrBuffer.ensureRemainingCapacity(wireBytes.length);
      ByteBuffer payloadBuffer = cdrBuffer.getBufferUnsafe();
      payloadBuffer.position(0);
      payloadBuffer.limit(wireBytes.length);
      payloadBuffer.put(wireBytes);
      if (flipAfterRead)
      {
         payloadBuffer.flip();
      }

      subscriberType.deserialize(cdrBuffer, receiveBuffer);
   }
}
