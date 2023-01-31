package us.ihmc.robotDataLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;

import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.concurrent.ConcurrentRingBuffer;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.multicastLogDataProtocol.modelLoaders.LogModelProvider;
import us.ihmc.robotDataLogger.dataBuffers.CustomLogDataPublisherType;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.robotDataLogger.handshake.SummaryProvider;
import us.ihmc.robotDataLogger.handshake.YoVariableHandShakeBuilder;
import us.ihmc.robotDataLogger.interfaces.BufferListenerInterface;
import us.ihmc.robotDataLogger.interfaces.DataProducer;
import us.ihmc.robotDataLogger.interfaces.RegistryPublisher;
import us.ihmc.robotDataLogger.listeners.VariableChangedListener;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;
import us.ihmc.robotDataLogger.websocket.server.WebsocketDataProducer;
import us.ihmc.util.PeriodicThreadSchedulerFactory;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

public class YoVariableServer implements RobotVisualizer, VariableChangedListener
{
   private static final int CHANGED_BUFFER_CAPACITY = 128;

   private final double dt;
   
   private final String name;
   private final LogModelProvider logModelProvider;
   private final DataServerSettings dataServerSettings;

   private String rootRegistryName = "main";

   private YoRegistry mainRegistry = null;
   private final ArrayList<RegistrySendBufferBuilder> registeredBuffers = new ArrayList<>();
   
   private final ArrayList<RegistryHolder> registryHolders = new ArrayList<>();
   
   // State
   private boolean started = false;
   private boolean stopped = false;

   // Servers
   private final DataProducer dataProducer;
   private YoVariableHandShakeBuilder handshakeBuilder;

   private volatile long latestTimestamp;

   private final SummaryProvider summaryProvider = new SummaryProvider();

   private final LogWatcher logWatcher = new LogWatcher();
   
   private BufferListenerInterface bufferListener = null;

   @Deprecated
   /**
    * A thread scheduler is not necessary anymore. This function is left in for backwards
    * compatibility.
    *
    * @param mainClazz
    * @param schedulerFactory
    * @param logModelProvider
    * @param dataServerSettings
    * @param dt
    */
   public YoVariableServer(Class<?> mainClazz, PeriodicThreadSchedulerFactory schedulerFactory, LogModelProvider logModelProvider,
                           DataServerSettings dataServerSettings, double dt)
   {
      this(mainClazz, logModelProvider, dataServerSettings, dt);
   }

   @Deprecated
   /**
    * A thread scheduler is not necessary anymore. This function is left in for backwards
    * compatibility.
    *
    * @param mainClazz
    * @param schedulerFactory
    * @param logModelProvider
    * @param dataServerSettings
    * @param dt
    */
   public YoVariableServer(String mainClazz, PeriodicThreadSchedulerFactory schedulerFactory, LogModelProvider logModelProvider,
                           DataServerSettings dataServerSettings, double dt)
   {
      this(mainClazz, logModelProvider, dataServerSettings, dt);
   }

   /**
    * Create a YoVariable server with mainClazz.getSimpleName(). For example, see other constructor.
    *
    * @param mainClazz
    * @param schedulerFactory
    * @param logModelProvider
    * @param dataServerSettings
    * @param dt
    */
   public YoVariableServer(Class<?> mainClazz, LogModelProvider logModelProvider, DataServerSettings dataServerSettings, double dt)
   {
      this(mainClazz.getSimpleName(), logModelProvider, dataServerSettings, dt);
   }

   /**
    * To create a YoVariableServer:
    * <ol>
    * <li>Create a YoRegistry</li>
    * <li>Add YoVariables</li>
    * <li>Create YoVariableServer</li>
    * <li>Set the YoVariableServer's main registry to the one you made</li>
    * <li>Call YoVariableServer.start()</li>
    * <li>Schedule a thread that calls YoVariableServer.update() periodically</li>
    * </ol>
    * Pseuo-code for starting a YoVariableServer:
    *
    * <pre>
    * {
    *    &#64;code
    *    YoRegistry registry = new YoRegistry("hello"); // cannot be "root", reserved word
    *    YoDouble doubleYo = new YoDouble("x", registry);
    *
    *    PeriodicNonRealtimeThreadSchedulerFactory schedulerFactory = new PeriodicNonRealtimeThreadSchedulerFactory();
    *    YoVariableServer yoVariableServer = new YoVariableServer("HelloYoServer", schedulerFactory, null, new LogSettings(false), 0.01);
    *    yoVariableServer.setMainRegistry(registry, null, null);
    *    yoVariableServer.start();
    *
    *    PeriodicThreadScheduler updateScheduler = schedulerFactory.createPeriodicThreadScheduler("update");
    *
    *    AtomicLong timestamp = new AtomicLong(); // must schedule updates yourself or the server will timeout
    *    updateScheduler.schedule(() ->
    *    {
    *       yoVariableServer.update(timestamp.getAndAdd(10000));
    *    }, 10, TimeUnit.MILLISECONDS);
    * }
    * </pre>
    *
    * @param mainClazz
    * @param schedulerFactory
    * @param logModelProvider
    * @param dataServerSettings
    * @param dt
    */
   public YoVariableServer(String mainClazz, LogModelProvider logModelProvider, DataServerSettings dataServerSettings, double dt)
   {
      this.dt = dt;
      this.name = mainClazz;
      this.logModelProvider = logModelProvider;
      this.dataServerSettings = dataServerSettings;
      
      dataProducer = new WebsocketDataProducer(this, logWatcher, dataServerSettings);

   }

   public void setRootRegistryName(String name)
   {
      rootRegistryName = name;
   }
   
   /**
    * Add a listener for new buffer data
    * 
    * This could be used to implement, for example, a in-memory logger
    * 
    * @param bufferListener
    */
   public synchronized void addBufferListener(BufferListenerInterface bufferListener)
   {
      if (started)
      {
         throw new RuntimeException("Server already started");
      }
      
      this.bufferListener = bufferListener;
   }

   public synchronized void start()
   {
      if (started)
      {
         throw new RuntimeException("Server already started");
      }
      if (stopped)
      {
         throw new RuntimeException("Cannot restart a YoVariable server.");
      }

      handshakeBuilder = new YoVariableHandShakeBuilder(rootRegistryName, dt);
      handshakeBuilder.setFrames(ReferenceFrame.getWorldFrame());
      handshakeBuilder.setSummaryProvider(summaryProvider);
     
      try
      {
         if(bufferListener != null)
         {
            bufferListener.allocateBuffers(registeredBuffers.size());
         }
         for (int i = 0; i < registeredBuffers.size(); i++)
         {
            RegistrySendBufferBuilder builder = registeredBuffers.get(i);
            YoRegistry registry = builder.getYoRegistry();
            
            try
            {
               ConcurrentRingBuffer<VariableChangedMessage> variableChangeData = new ConcurrentRingBuffer<>(new VariableChangedMessage.Builder(), CHANGED_BUFFER_CAPACITY);
               RegistryPublisher publisher = dataProducer.createRegistryPublisher(builder, bufferListener);
               registryHolders.add(new RegistryHolder(registry, publisher, variableChangeData));
               
               publisher.start();
            }
            catch (IOException e)
            {
               throw new RuntimeException(e);
            }
            
         }
         
         DataServerServerContent content = new DataServerServerContent(rootRegistryName, handshakeBuilder.getHandShake(), logModelProvider, dataServerSettings);
               
         if(bufferListener != null)
         {
            bufferListener.setContent(content);
         }

         dataProducer.setDataServerContent(content);
         dataProducer.announce();

      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      started = true;
   }

   public RegistryHolder getRegistryHolder(YoRegistry registry)
   {
      for(int i = 0; i < registryHolders.size(); i++)
      {
         RegistryHolder registryHolder = registryHolders.get(i);
         if(registryHolder.registry == registry)
         {
            return registryHolder;
         }
      }
      throw new RuntimeException("Registry " + registry.getName() + " not registed with addRegistry() or setMainRegistry()");
   }
   
   @Override
   public synchronized void close()
   {
      if (started && !stopped)
      {
         stopped = true;
         for (int i = 0; i < registryHolders.size(); i++)
         {
            registryHolders.get(i).publisher.stop();
         }
         dataProducer.remove();

         
         if(bufferListener != null)
         {
            bufferListener.close();
         }
      }
   }

   /**
    * Update main buffer data. Note: If the timestamp is not increasing between updates(), no data
    * might be send to clients.
    *
    * @param timestamp timestamp to send to logger
    */
   @Override
   public void update(long timestamp)
   {
      update(timestamp, mainRegistry);
   }

   /**
    * Update registry data Note: If the timestamp is not increasing between updates(), no data might be
    * send to clients.
    *
    * @param timestamp timestamp to send to the logger
    * @param registry  Top level registry to update
    */
   @Override
   public void update(long timestamp, YoRegistry registry)
   {
      if (!started || stopped)
      {
         return;
      }
      if (registry == mainRegistry)
      {
         dataProducer.publishTimestamp(timestamp);
         latestTimestamp = timestamp;
      }

      RegistryHolder registryHolder = getRegistryHolder(registry);

      registryHolder.publisher.update(timestamp);
      updateChangedVariables(registryHolder);

      logWatcher.update(timestamp);
   }

   private void updateChangedVariables(RegistryHolder rootRegistry)
   {
      ConcurrentRingBuffer<VariableChangedMessage> buffer = rootRegistry.variableChangeData;
      buffer.poll();
      VariableChangedMessage msg;
      while ((msg = buffer.read()) != null)
      {
         msg.getVariable().setValueFromDouble(msg.getVal());
      }
      buffer.flush();
   }

   @Override
   public void addRegistry(YoRegistry registry, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      if (mainRegistry == null)
      {
         throw new RuntimeException("Main registry is not set. Set main registry first");
      }

      registeredBuffers.add(new RegistrySendBufferBuilder(registry, yoGraphicsListRegistry));
   }

   @Override
   public void setMainRegistry(YoRegistry registry, List<? extends JointBasics> jointsToPublish, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      if (mainRegistry != null)
      {
         throw new RuntimeException("Main registry is already set");
      }
      registeredBuffers.add(new RegistrySendBufferBuilder(registry, jointsToPublish, yoGraphicsListRegistry));
      mainRegistry = registry;
   }

   private YoVariable findVariableInRegistries(String variableName)
   {

      for (RegistrySendBufferBuilder buffer : registeredBuffers)
      {
         YoRegistry registry = buffer.getYoRegistry();
         YoVariable ret = registry.findVariable(variableName);
         if (ret != null)
         {
            return ret;
         }
      }
      return null;
   }

   public void createSummary(YoVariable isWalkingVariable)
   {
      createSummary(isWalkingVariable.getFullNameString());
   }

   public void createSummary(String summaryTriggerVariable)
   {
      if (findVariableInRegistries(summaryTriggerVariable) == null)
      {
         throw new RuntimeException("Variable " + summaryTriggerVariable + " is not registered with the logger");
      }
      summaryProvider.setSummarize(true);
      summaryProvider.setSummaryTriggerVariable(summaryTriggerVariable);
   }

   public void addSummarizedVariable(String variable)
   {
      if (findVariableInRegistries(variable) == null)
      {
         throw new RuntimeException("Variable " + variable + " is not registered with the logger");
      }
      summaryProvider.addSummarizedVariable(variable);
   }

   public void addSummarizedVariable(YoVariable variable)
   {
      summaryProvider.addSummarizedVariable(variable);
   }

   @Override
   public void changeVariable(int id, double newValue)
   {
      VariableChangedMessage message;
      ImmutablePair<YoVariable, YoRegistry> variableAndRootRegistry = handshakeBuilder.getVariablesAndRootRegistries().get(id);

      RegistryHolder holder = getRegistryHolder(variableAndRootRegistry.getRight());
      ConcurrentRingBuffer<VariableChangedMessage> buffer = holder.variableChangeData;
      while ((message = buffer.next()) == null)
      {
         ThreadTools.sleep(1);
      }

      if (message != null)
      {
         message.setVariable(variableAndRootRegistry.getLeft());
         message.setVal(newValue);
         buffer.commit();
      }

   }

   @Override
   public long getLatestTimestamp()
   {
      return latestTimestamp;
   }

   public boolean isLogging()
   {
      return logWatcher.isLogging();
   }

   private class RegistryHolder
   {
      private final YoRegistry registry;
      private final RegistryPublisher publisher;
      private final ConcurrentRingBuffer<VariableChangedMessage> variableChangeData;

      public RegistryHolder(YoRegistry registry, RegistryPublisher publisher, ConcurrentRingBuffer<VariableChangedMessage> variableChangeData)
      {
         this.registry = registry;
         this.publisher = publisher;
         this.variableChangeData = variableChangeData;
      }
      
   }
}
