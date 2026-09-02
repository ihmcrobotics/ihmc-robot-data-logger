package us.ihmc.robotDataLogger.jointState;

import logger_msgs.JointType;

public interface JointHolder
{
   public String getName();

   public JointType getJointType();

   public int getNumberOfStateVariables();

   public void get(double[] buffer, int offset);
}
