package us.ihmc.robotDataLogger;

import java.util.Collections;
import java.util.List;

import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.registry.YoRegistry;

public interface RobotVisualizer
{
   void update(long timestamp);

   void update(long timestamp, YoRegistry registry);

   default void setMainRegistry(YoRegistry registry, YoGraphicsListRegistry scs1YoGraphics)
   {
      setMainRegistry(registry, Collections.emptyList(), scs1YoGraphics, null);
   }

   default void setMainRegistry(YoRegistry registry, YoGraphicsListRegistry scs1YoGraphics, YoGraphicGroupDefinition scs2YoGraphics)
   {
      setMainRegistry(registry, Collections.emptyList(), scs1YoGraphics, scs2YoGraphics);
   }

   default void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry scs1YoGraphics)
   {
      setMainRegistry(registry, collectJoints(rootBody), scs1YoGraphics, null);
   }

   default void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry scs1YoGraphics, YoGraphicGroupDefinition scs2YoGraphics)
   {
      setMainRegistry(registry, collectJoints(rootBody), scs1YoGraphics, scs2YoGraphics);
   }

   default void setMainRegistry(YoRegistry registry, List<? extends JointBasics> jointsToPublish, YoGraphicsListRegistry scs1YoGraphics)
   {
      setMainRegistry(registry, jointsToPublish, scs1YoGraphics, null);
   }

   void setMainRegistry(YoRegistry registry,
                        List<? extends JointBasics> jointsToPublish,
                        YoGraphicsListRegistry scs1YoGraphics,
                        YoGraphicGroupDefinition scs2YoGraphics);

   default void addRegistry(YoRegistry registry, YoGraphicsListRegistry scs1YoGraphics)
   {
      addRegistry(registry, scs1YoGraphics, null);
   }

   void addRegistry(YoRegistry registry, YoGraphicsListRegistry scs1YoGraphics, YoGraphicGroupDefinition scs2YoGraphics);

   void close();

   long getLatestTimestamp();

   static List<? extends JointBasics> collectJoints(RigidBodyBasics rootBody)
   {
      return rootBody == null ? Collections.emptyList() : rootBody.subtreeJointList(JointBasics.class);
   }
}
