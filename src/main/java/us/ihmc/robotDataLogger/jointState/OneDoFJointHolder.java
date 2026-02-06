package us.ihmc.robotDataLogger.jointState;

import logger_msgs.msg.dds.JointType;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;

public class OneDoFJointHolder implements JointHolder
{
   private static final JointType ONEDOFJOINTTYPE = new JointType();
   static
   {
      ONEDOFJOINTTYPE.setType(JointType.ONEDOFJOINT);
   }

   private final OneDoFJointBasics joint;

   public OneDoFJointHolder(OneDoFJointBasics joint)
   {
      this.joint = joint;
   }

   @Override
   public JointType getJointType()
   {
      return ONEDOFJOINTTYPE;
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
