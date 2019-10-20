package us.ihmc.robotDataLogger.jointState;

import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.robotDataLogger.JointType;

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
      return JointType.OneDoFJoint;
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
