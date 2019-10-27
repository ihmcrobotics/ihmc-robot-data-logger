package us.ihmc.robotDataLogger.jointState;

import java.nio.DoubleBuffer;
import java.nio.LongBuffer;

import us.ihmc.euclid.orientation.interfaces.Orientation3DBasics;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Tuple3DBasics;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.euclid.tuple4D.interfaces.QuaternionReadOnly;
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

   public QuaternionReadOnly getRotation()
   {
      return rotation;
   }

   public void getRotation(Orientation3DBasics orientationToPack)
   {
      orientationToPack.set(rotation);
   }

   public Vector3DReadOnly getTranslation()
   {
      return translation;
   }

   public void getTranslation(Tuple3DBasics translationToPack)
   {
      translationToPack.set(translation);
   }
   
   public Vector3DReadOnly getTwistAngularPart()
   {
      return twist.getAngularPart();
   }

   public Vector3DReadOnly getTwistLinearPart()
   {
      return twist.getLinearPart();
   }

   public void getTwistAngularPart(Tuple3DBasics twistAngularPartToPack)
   {
      twistAngularPartToPack.set(twist.getAngularPart());
   }

   public void getTwistLinearPart(Tuple3DBasics twistLinearPartToPack)
   {
      twistLinearPartToPack.set(twist.getLinearPart());
   }
}
