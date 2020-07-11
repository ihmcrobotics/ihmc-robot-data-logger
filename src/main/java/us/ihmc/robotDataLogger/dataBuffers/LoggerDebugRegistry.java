package us.ihmc.robotDataLogger.dataBuffers;

import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoInteger;

import java.util.concurrent.atomic.AtomicLong;

public class LoggerDebugRegistry
{
   private static final AtomicLong counter = new AtomicLong();

   private final YoRegistry registry;
   private final YoInteger fullCircularBufferCounter;
   private final YoInteger lostTickInCircularBuffer;

   public LoggerDebugRegistry(YoRegistry parentRegistry)
   {
      String registryName = "LoggerDebugRegistry";
      long count = counter.getAndIncrement();
      if (count > 0)
      {
         registryName += count;
      }
      registry = new YoRegistry(registryName);
      fullCircularBufferCounter = new YoInteger("FullCircularBuffer", registry);
      lostTickInCircularBuffer = new YoInteger("lostTickInCircularBuffer", registry);

      parentRegistry.addChild(registry);
   }

   public void circularBufferFull()
   {
      fullCircularBufferCounter.increment();
   }

   public void lostTickInCircularBuffer()
   {
      lostTickInCircularBuffer.increment();
   }

}
