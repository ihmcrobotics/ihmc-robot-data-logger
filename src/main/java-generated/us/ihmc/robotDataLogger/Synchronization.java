package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class Synchronization extends Packet<Synchronization> implements Settable<Synchronization>, EpsilonComparable<Synchronization>
{
   public double offset_;
   // Index offset from the start of the parent log
   public double jogRate_;

   public Synchronization()
   {
   }

   public Synchronization(Synchronization other)
   {
      this();
      set(other);
   }

   public void set(Synchronization other)
   {
      offset_ = other.offset_;

      jogRate_ = other.jogRate_;

   }

   public void setOffset(double offset)
   {
      offset_ = offset;
   }
   public double getOffset()
   {
      return offset_;
   }

   // Index offset from the start of the parent log
   public void setJogRate(double jogRate)
   {
      jogRate_ = jogRate;
   }
   // Index offset from the start of the parent log
   public double getJogRate()
   {
      return jogRate_;
   }


   public static Supplier<SynchronizationPubSubType> getPubSubType()
   {
      return SynchronizationPubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return SynchronizationPubSubType::new;
   }

   @Override
   public boolean epsilonEquals(Synchronization other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.offset_, other.offset_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.jogRate_, other.jogRate_, epsilon)) return false;


      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof Synchronization)) return false;

      Synchronization otherMyClass = (Synchronization) other;

      if(this.offset_ != otherMyClass.offset_) return false;

      if(this.jogRate_ != otherMyClass.jogRate_) return false;


      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("Synchronization {");
      builder.append("offset=");
      builder.append(this.offset_);      builder.append(", ");
      builder.append("jogRate=");
      builder.append(this.jogRate_);
      builder.append("}");
      return builder.toString();
   }
}
