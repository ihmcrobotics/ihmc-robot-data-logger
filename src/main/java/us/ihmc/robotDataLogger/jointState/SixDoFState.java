package us.ihmc.robotDataLogger.jointState;

import java.nio.DoubleBuffer;
import java.nio.LongBuffer;

import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.mecano.spatial.Twist;
import us.ihmc.robotDataLogger.JointType;

public class SixDoFState extends JointState
{
   public static final int numberOfStateVariables = 13;

   private final Quaternion rotation = new Quaternion();

   private final Vector3D translation = new Vector3D();
   private final Twist twist = new Twist();

   public SixDoFState(String name)
   {
      super(name, JointType.SiXDoFJoint);
   }

   @Override
   public void get(double[] array)
   {
      array[0] = rotation.getS();
      array[1] = rotation.getX();
      array[2] = rotation.getY();
      array[3] = rotation.getZ();

      array[4] = translation.getX();
      array[5] = translation.getY();
      array[6] = translation.getZ();

      twist.get(7, array);
   }

   @Override
   public void update(DoubleBuffer buffer)
   {

      double qs = buffer.get();
      double qx = buffer.get();
      double qy = buffer.get();
      double qz = buffer.get();
      rotation.set(qx, qy, qz, qs);
      translation.setX(buffer.get());
      translation.setY(buffer.get());
      translation.setZ(buffer.get());

      twist.setAngularPartX(buffer.get());
      twist.setAngularPartY(buffer.get());
      twist.setAngularPartZ(buffer.get());

      twist.setLinearPartX(buffer.get());
      twist.setLinearPartY(buffer.get());
      twist.setLinearPartZ(buffer.get());
   }

   @Override
   public void update(LongBuffer buffer)
   {

      double qs = Double.longBitsToDouble(buffer.get());
      double qx = Double.longBitsToDouble(buffer.get());
      double qy = Double.longBitsToDouble(buffer.get());
      double qz = Double.longBitsToDouble(buffer.get());
      rotation.set(qx, qy, qz, qs);
      translation.setX(Double.longBitsToDouble(buffer.get()));
      translation.setY(Double.longBitsToDouble(buffer.get()));
      translation.setZ(Double.longBitsToDouble(buffer.get()));

      twist.setAngularPartX(Double.longBitsToDouble(buffer.get()));
      twist.setAngularPartY(Double.longBitsToDouble(buffer.get()));
      twist.setAngularPartZ(Double.longBitsToDouble(buffer.get()));

      twist.setLinearPartX(Double.longBitsToDouble(buffer.get()));
      twist.setLinearPartY(Double.longBitsToDouble(buffer.get()));
      twist.setLinearPartZ(Double.longBitsToDouble(buffer.get()));
   }

   @Override
   public void get(LongBuffer buffer)
   {
      buffer.put(Double.doubleToLongBits(rotation.getS()));
      buffer.put(Double.doubleToLongBits(rotation.getX()));
      buffer.put(Double.doubleToLongBits(rotation.getY()));
      buffer.put(Double.doubleToLongBits(rotation.getZ()));

      buffer.put(Double.doubleToLongBits(translation.getX()));
      buffer.put(Double.doubleToLongBits(translation.getY()));
      buffer.put(Double.doubleToLongBits(translation.getZ()));

      buffer.put(Double.doubleToLongBits(twist.getAngularPartX()));
      buffer.put(Double.doubleToLongBits(twist.getAngularPartY()));
      buffer.put(Double.doubleToLongBits(twist.getAngularPartZ()));

      buffer.put(Double.doubleToLongBits(twist.getLinearPartX()));
      buffer.put(Double.doubleToLongBits(twist.getLinearPartY()));
      buffer.put(Double.doubleToLongBits(twist.getLinearPartZ()));
   }

   @Override
   public int getNumberOfStateVariables()
   {
      return numberOfStateVariables;
   }

   public void getRotation(RotationMatrix rotationMatrix)
   {
      rotationMatrix.set(rotation);
   }

   public void getTranslation(Vector3D tempVector)
   {
      tempVector.set(translation);
   }

   public void getTwistAngularPart(Vector3D tempVector)
   {
      tempVector.set(twist.getAngularPart());
   }

   public void getTwistLinearPart(Vector3D tempVector)
   {
      tempVector.set(twist.getLinearPart());
   }
}
