package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class SCS1AppearanceDefinitionMessage extends Packet<SCS1AppearanceDefinitionMessage> implements Settable<SCS1AppearanceDefinitionMessage>, EpsilonComparable<SCS1AppearanceDefinitionMessage>
{
   public double r_;
   public double g_;
   public double b_;
   public double transparency_;

   public SCS1AppearanceDefinitionMessage()
   {
   }

   public SCS1AppearanceDefinitionMessage(SCS1AppearanceDefinitionMessage other)
   {
      this();
      set(other);
   }

   public void set(SCS1AppearanceDefinitionMessage other)
   {
      r_ = other.r_;

      g_ = other.g_;

      b_ = other.b_;

      transparency_ = other.transparency_;

   }

   public void setR(double r)
   {
      r_ = r;
   }
   public double getR()
   {
      return r_;
   }

   public void setG(double g)
   {
      g_ = g;
   }
   public double getG()
   {
      return g_;
   }

   public void setB(double b)
   {
      b_ = b;
   }
   public double getB()
   {
      return b_;
   }

   public void setTransparency(double transparency)
   {
      transparency_ = transparency;
   }
   public double getTransparency()
   {
      return transparency_;
   }


   public static Supplier<SCS1AppearanceDefinitionMessagePubSubType> getPubSubType()
   {
      return SCS1AppearanceDefinitionMessagePubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return SCS1AppearanceDefinitionMessagePubSubType::new;
   }

   @Override
   public boolean epsilonEquals(SCS1AppearanceDefinitionMessage other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.r_, other.r_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.g_, other.g_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.b_, other.b_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.transparency_, other.transparency_, epsilon)) return false;


      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof SCS1AppearanceDefinitionMessage)) return false;

      SCS1AppearanceDefinitionMessage otherMyClass = (SCS1AppearanceDefinitionMessage) other;

      if(this.r_ != otherMyClass.r_) return false;

      if(this.g_ != otherMyClass.g_) return false;

      if(this.b_ != otherMyClass.b_) return false;

      if(this.transparency_ != otherMyClass.transparency_) return false;


      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("SCS1AppearanceDefinitionMessage {");
      builder.append("r=");
      builder.append(this.r_);      builder.append(", ");
      builder.append("g=");
      builder.append(this.g_);      builder.append(", ");
      builder.append("b=");
      builder.append(this.b_);      builder.append(", ");
      builder.append("transparency=");
      builder.append(this.transparency_);
      builder.append("}");
      return builder.toString();
   }
}
