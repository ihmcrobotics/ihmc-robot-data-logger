package us.ihmc.robotDataLogger.jointState;

import logger_msgs.msg.dds.JointType;

import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.List;

public abstract class JointState
{
   private final JointType type;
   private final String name;

   public JointState(String name, JointType type)
   {
      this.name = name;
      this.type = type;
   }

   public String getName()
   {
      return name;
   }

   public JointType getType()
   {
      return type;
   }

   public abstract void update(LongBuffer buffer);

   public abstract void update(DoubleBuffer buffer);

   public abstract void get(double[] array);

   public abstract void get(LongBuffer buffer);

   public abstract int getNumberOfStateVariables();

   public static int getNumberOfVariables(JointType type)
   {
      return getNumberOfVariables(type.getType());
   }

   public static int getNumberOfVariables(byte type)
   {
      switch (type)
      {
         case JointType.ONEDOFJOINT:
            return OneDoFState.numberOfStateVariables;
         case JointType.SIXDOFJOINT:
            return SixDoFState.numberOfStateVariables;
         default:
            throw new RuntimeException("Unknown joint type" + type);
      }
   }

   public static JointState createJointState(String name, JointType type)
   {
      return createJointState(name, type.getType());
   }

   public static JointState createJointState(String name, byte type)
   {
      switch (type)
      {
         case JointType.ONEDOFJOINT:
            return new OneDoFState(name);
         case JointType.SIXDOFJOINT:
            return new SixDoFState(name);
         default:
            throw new RuntimeException("Unknown joint type" + type);
      }
   }

   public static int getNumberOfJointStates(List<JointState> jointStates)
   {
      int numberOfJointStates = 0;
      for (int i = 0; i < jointStates.size(); i++)
      {
         numberOfJointStates += jointStates.get(i).getNumberOfStateVariables();
      }
      return numberOfJointStates;
   }
}
