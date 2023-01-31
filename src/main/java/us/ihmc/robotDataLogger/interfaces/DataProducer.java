package us.ihmc.robotDataLogger.interfaces;

import java.io.IOException;

import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.dataBuffers.CustomLogDataPublisherType;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;

public interface DataProducer
{

   /**
    * Deactivate the data producer. After calling this function, the producer cannot be reactivated
    */
   void remove();

   /**
    * Set the handshake data Required
    *
    * @param handshake
    */
   void setDataServerContent(DataServerServerContent dataServerServerContent);

   /**
    * Activate the producer. This will publish the model, handshake and logger announcement to the
    * logger
    *
    * @throws IOException
    */
   void announce() throws IOException;

   /**
    * Publisher a timestamp update
    * 
    * @param timestamp
    * @throws IOException
    */
   void publishTimestamp(long timestamp);

   RegistryPublisher createRegistryPublisher(RegistrySendBufferBuilder builder, BufferListenerInterface bufferListener) throws IOException;

}