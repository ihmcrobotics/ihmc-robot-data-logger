package us.ihmc.robotDataLogger;

import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.yoVariables.registry.YoRegistry;

public interface RobotVisualizer
{
   public void update(long timestamp);

   public void update(long timestamp, YoRegistry registry);

   public void setMainRegistry(YoRegistry registry, RigidBodyBasics rootBody, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void addRegistry(YoRegistry registry, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void close();

   public long getLatestTimestamp();
}
