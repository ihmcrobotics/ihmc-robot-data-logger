package us.ihmc.robotDataLogger.dataBuffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.robotDataLogger.RobotVisualizer;
import us.ihmc.robotDataLogger.jointState.JointHolder;
import us.ihmc.robotDataLogger.jointState.JointHolderFactory;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistrySendBufferBuilder implements us.ihmc.concurrent.Builder<RegistrySendBuffer>
{
   private final YoRegistry registry;
   private final List<? extends JointBasics> jointsToPublish;

   private final List<YoVariable> variables = new ArrayList<>();
   private final List<JointHolder> jointHolders = new ArrayList<>();

   private final LoggerDebugRegistry loggerDebugRegistry;

   private final YoGraphicsListRegistry scs1Graphics;
   private final YoGraphicGroupDefinition scs2Graphics;

   private int registryID = -1;

   public RegistrySendBufferBuilder(YoRegistry registry)
   {
      this(registry, Collections.emptyList(), null);
   }

   public RegistrySendBufferBuilder(YoRegistry registry, YoGraphicsListRegistry scs1Graphics)
   {
      this(registry, Collections.emptyList(), scs1Graphics);
   }

   public RegistrySendBufferBuilder(YoRegistry registry, YoGraphicsListRegistry scs1Graphics, YoGraphicGroupDefinition scs2Graphics)
   {
      this(registry, Collections.emptyList(), scs1Graphics, scs2Graphics);
   }

   public RegistrySendBufferBuilder(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry scs1Graphics)
   {
      this(registry, RobotVisualizer.collectJoints(rootBody), scs1Graphics);
   }

   public RegistrySendBufferBuilder(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry scs1Graphics, YoGraphicGroupDefinition scs2Graphics)
   {
      this(registry, RobotVisualizer.collectJoints(rootBody), scs1Graphics, scs2Graphics);
   }

   public RegistrySendBufferBuilder(YoRegistry registry, List<? extends JointBasics> jointsToPublish, YoGraphicsListRegistry scs1Graphics)
   {
      this(registry, jointsToPublish, scs1Graphics, null);
   }

   public RegistrySendBufferBuilder(YoRegistry registry,
                                    List<? extends JointBasics> jointsToPublish,
                                    YoGraphicsListRegistry scs1Graphics,
                                    YoGraphicGroupDefinition scs2Graphics)
   {
      this.registry = registry;
      this.jointsToPublish = jointsToPublish;
      this.scs1Graphics = scs1Graphics;
      this.scs2Graphics = scs2Graphics;

      loggerDebugRegistry = new LoggerDebugRegistry(registry);
   }

   public YoRegistry getYoRegistry()
   {
      return registry;
   }

   public List<YoVariable> getVariables()
   {
      return variables;
   }

   public void build(int registryID)
   {
      this.registryID = registryID;

      if (jointsToPublish != null)
      {
         for (JointBasics joint : jointsToPublish)
         {
            JointHolder jointHolder = JointHolderFactory.getJointHolder(joint);
            jointHolders.add(jointHolder);
         }
      }
   }

   public List<JointHolder> getJointHolders()
   {
      return jointHolders;
   }

   public YoGraphicsListRegistry getSCS1YoGraphics()
   {
      return scs1Graphics;
   }

   public YoGraphicGroupDefinition getSCS2YoGraphics()
   {
      return scs2Graphics;
   }

   @Override
   public RegistrySendBuffer newInstance()
   {
      if (registryID == -1)
      {
         throw new RuntimeException("RegistrySendBufferBuilder.build() not called");
      }

      if (variables.size() == 0)
      {
         throw new RuntimeException("Variables not populated");
      }

      return new RegistrySendBuffer(registryID, variables, jointHolders);
   }

   public int getNumberOfJointStates()
   {
      return getNumberOfJointStates(jointHolders);
   }

   public int getNumberOfVariables()
   {
      return variables.size();
   }

   public static int getNumberOfJointStates(List<JointHolder> jointHolders)
   {
      int numberOfJointStates = 0;
      for (int i = 0; i < jointHolders.size(); i++)
      {
         numberOfJointStates += jointHolders.get(i).getNumberOfStateVariables();
      }
      return numberOfJointStates;
   }

   public LoggerDebugRegistry getLoggerDebugRegistry()
   {
      return loggerDebugRegistry;
   }
}