package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class ChildLog extends Packet<ChildLog> implements Settable<ChildLog>, EpsilonComparable<ChildLog>
{
   public java.lang.StringBuilder childName_;
   // Name of the child log
   public us.ihmc.robotDataLogger.Synchronization synchronization_;

   public ChildLog()
   {
      childName_ = new java.lang.StringBuilder(255);
      synchronization_ = new us.ihmc.robotDataLogger.Synchronization();
   }

   public ChildLog(ChildLog other)
   {
      this();
      set(other);
   }

   public void set(ChildLog other)
   {
      childName_.setLength(0);
      childName_.append(other.childName_);

      us.ihmc.robotDataLogger.SynchronizationPubSubType.staticCopy(other.synchronization_, synchronization_);
   }

   public void setChildName(java.lang.String childName)
   {
      childName_.setLength(0);
      childName_.append(childName);
   }

   public java.lang.String getChildNameAsString()
   {
      return getChildName().toString();
   }
   public java.lang.StringBuilder getChildName()
   {
      return childName_;
   }


   // Name of the child log
   public us.ihmc.robotDataLogger.Synchronization getSynchronization()
   {
      return synchronization_;
   }


   public static Supplier<ChildLogPubSubType> getPubSubType()
   {
      return ChildLogPubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return ChildLogPubSubType::new;
   }

   @Override
   public boolean epsilonEquals(ChildLog other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.childName_, other.childName_, epsilon)) return false;

      if (!this.synchronization_.epsilonEquals(other.synchronization_, epsilon)) return false;

      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof ChildLog)) return false;

      ChildLog otherMyClass = (ChildLog) other;

      if (!us.ihmc.idl.IDLTools.equals(this.childName_, otherMyClass.childName_)) return false;

      if (!this.synchronization_.equals(otherMyClass.synchronization_)) return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("ChildLog {");
      builder.append("childName=");
      builder.append(this.childName_);      builder.append(", ");
      builder.append("synchronization=");
      builder.append(this.synchronization_);
      builder.append("}");
      return builder.toString();
   }
}
