package us.ihmc.robotDataLogger.interfaces;

import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBuffer;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;

public interface BufferListenerInterface
{
   void setContent(DataServerServerContent content);
   
   /**
    * Called before addBuffer with the total number of buffers
    * 
    * @param numberOfRegistries
    */
   void allocateBuffers(int numberOfRegistries);

   /**
    * Each buffer gets added with addBuffer
    * @param bufferID
    * @param builder
    */
   void addBuffer(int bufferID, RegistrySendBufferBuilder builder);

   /**
    * Update buffer with buffer ID
    * 
    * @param bufferID
    * @param buffer
    */
   void updateBuffer(int bufferID, RegistrySendBuffer buffer);

   
   /**
    * Called before the first updateBuffer() call
    */
   void start();
   
   /**
    * Called when the variable server is closed
    */
   void close();


   
}
