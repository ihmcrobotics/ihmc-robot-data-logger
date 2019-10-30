package us.ihmc.robotDataLogger.jointState;

import java.nio.DoubleBuffer;
import java.nio.LongBuffer;

import us.ihmc.robotDataLogger.JointType;

public class OneDoFState extends JointState
{
   public static final int numberOfStateVariables = 2;

   private double q;
   private double qd;

   public OneDoFState(String name)
   {
      super(name, JointType.OneDoFJoint);
   }

   @Override
   public void update(DoubleBuffer buffer)
   {
      q = buffer.get();
      qd = buffer.get();
   }

   @Override
   public void update(LongBuffer buffer)
   {
      q = Double.longBitsToDouble(buffer.get());
      qd = Double.longBitsToDouble(buffer.get());
   }

   public double getQ()
   {
      return q;
   }

   public double getQd()
   {
      return qd;
   }

   @Override
   public void get(double[] array)
   {
      array[0] = q;
      array[1] = qd;
   }

   @Override
   public void get(LongBuffer buffer)
   {
      buffer.put(Double.doubleToLongBits(q));
      buffer.put(Double.doubleToLongBits(qd));
   }

   @Override
   public int getNumberOfStateVariables()
   {
      return numberOfStateVariables;
   }

}
