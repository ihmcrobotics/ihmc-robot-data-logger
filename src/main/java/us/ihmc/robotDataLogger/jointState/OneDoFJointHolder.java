package us.ihmc.robotDataLogger.jointState;

import logger_msgs.JointType;
import logger_msgs.MessageTypes;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;

public class OneDoFJointHolder implements JointHolder
{
   private final OneDoFJointBasics joint;

   public OneDoFJointHolder(OneDoFJointBasics joint)
   {
      this.joint = joint;
   }

   @Override
   public JointType getJointType()
   {
      return MessageTypes.ONE_DOF_JOINT;
   }

   @Override
   public int getNumberOfStateVariables()
   {
      return 2;
   }

   @Override
   public void get(double[] buffer, int offset)
   {
      buffer[offset + 0] = joint.getQ();
      buffer[offset + 1] = joint.getQd();
   }

   @Override
   public String getName()
   {
      return joint.getName();
   }

}
