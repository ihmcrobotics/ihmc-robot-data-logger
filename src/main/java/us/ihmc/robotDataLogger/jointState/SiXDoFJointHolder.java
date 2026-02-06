package us.ihmc.robotDataLogger.jointState;

import logger_msgs.msg.dds.JointType;
import us.ihmc.euclid.tuple3D.interfaces.Tuple3DReadOnly;
import us.ihmc.euclid.tuple4D.interfaces.QuaternionReadOnly;
import us.ihmc.mecano.multiBodySystem.SixDoFJoint;

public class SiXDoFJointHolder implements JointHolder
{
   private static final JointType SIXDOFJOINTTYPE = new JointType();
   static
   {
      SIXDOFJOINTTYPE.setType(JointType.SIXDOFJOINT);
   }


   private final SixDoFJoint inverseDynamicsJoint;

   public SiXDoFJointHolder(SixDoFJoint joint)
   {
      inverseDynamicsJoint = joint;
   }

   @Override
   public JointType getJointType()
   {
      return SIXDOFJOINTTYPE;
   }

   @Override
   public int getNumberOfStateVariables()
   {
      return 13; // quaternion + position + angular velocity + linear velocity
   }

   @Override
   public void get(double[] buffer, int offset)
   {
      QuaternionReadOnly rotation = inverseDynamicsJoint.getJointPose().getOrientation();
      Tuple3DReadOnly translation = inverseDynamicsJoint.getJointPose().getPosition();
      Tuple3DReadOnly angularVelocity = inverseDynamicsJoint.getJointTwist().getAngularPart();
      Tuple3DReadOnly linearVelocity = inverseDynamicsJoint.getJointTwist().getLinearPart();

      buffer[offset++] = rotation.getS();
      buffer[offset++] = rotation.getX();
      buffer[offset++] = rotation.getY();
      buffer[offset++] = rotation.getZ();

      buffer[offset++] = translation.getX();
      buffer[offset++] = translation.getY();
      buffer[offset++] = translation.getZ();

      buffer[offset++] = angularVelocity.getX();
      buffer[offset++] = angularVelocity.getY();
      buffer[offset++] = angularVelocity.getZ();

      buffer[offset++] = linearVelocity.getX();
      buffer[offset++] = linearVelocity.getY();
      buffer[offset] = linearVelocity.getZ();
   }

   @Override
   public String getName()
   {
      return inverseDynamicsJoint.getName();
   }
}
