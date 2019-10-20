package us.ihmc.multicastLogDataProtocol.modelLoaders;

import us.ihmc.robotics.robotDescription.RobotDescription;

public interface LogModelLoader
{
   public void load(String modelName, byte[] model, String[] resourceDirectories, byte[] resourceZip);
   public RobotDescription createRobot();
   
   public String getModelName();
   public byte[] getModel();
   public String[] getResourceDirectories();
   public byte[] getResourceZip();
}
