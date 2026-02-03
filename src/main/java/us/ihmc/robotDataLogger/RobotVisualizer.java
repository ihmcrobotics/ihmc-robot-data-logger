package us.ihmc.robotDataLogger;

import java.util.Collections;
import java.util.List;

import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.registry.YoRegistry;

public interface RobotVisualizer
{
   void update(long timestamp);

   void update(long timestamp, YoRegistry registry);

   default void setMainRegistry(YoRegistry registry)
   {
      setMainRegistry(registry, Collections.emptyList(), null);
   }

   default void setMainRegistry(YoRegistry registry, YoGraphicGroupDefinition scs2YoGraphics)
   {
      setMainRegistry(registry, Collections.emptyList(), scs2YoGraphics);
   }

   default void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody)
   {
      setMainRegistry(registry, collectJoints(rootBody), null);
   }

   default void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicGroupDefinition scs2YoGraphics)
   {
      setMainRegistry(registry, collectJoints(rootBody), scs2YoGraphics);
   }

   default void setMainRegistry(YoRegistry registry, List<? extends JointBasics> jointsToPublish)
   {
      setMainRegistry(registry, jointsToPublish, null);
   }

   void setMainRegistry(YoRegistry registry,
                        List<? extends JointBasics> jointsToPublish,
                        YoGraphicGroupDefinition scs2YoGraphics);

   default void addRegistry(YoRegistry registry)
   {
      addRegistry(registry,null);
   }

   void addRegistry(YoRegistry registry, YoGraphicGroupDefinition scs2YoGraphics);

   void close();

   long getLatestTimestamp();

   static List<? extends JointBasics> collectJoints(RigidBodyBasics rootBody)
   {
      return rootBody == null ? Collections.emptyList() : rootBody.subtreeJointList(JointBasics.class);
   }
}
