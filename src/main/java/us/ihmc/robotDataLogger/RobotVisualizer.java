package us.ihmc.robotDataLogger;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.mecano.multiBodySystem.iterators.SubtreeStreams;
import us.ihmc.yoVariables.registry.YoRegistry;

public interface RobotVisualizer
{
   public void update(long timestamp);

   public void update(long timestamp, YoRegistry registry);

   default void setMainRegistry(YoRegistry registry, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      setMainRegistry(registry, Collections.emptyList(), yoGraphicsListRegistry);
   }

   default void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      setMainRegistry(registry,
                      rootBody == null ? Collections.emptyList() : SubtreeStreams.fromChildren(JointBasics.class, rootBody).collect(Collectors.toList()),
                      yoGraphicsListRegistry);
   }

   public void setMainRegistry(YoRegistry registry, List<? extends JointBasics> jointsToPublish, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void addRegistry(YoRegistry registry, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void close();

   public long getLatestTimestamp();
}
