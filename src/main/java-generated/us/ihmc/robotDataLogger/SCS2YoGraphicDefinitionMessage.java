package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class SCS2YoGraphicDefinitionMessage extends Packet<SCS2YoGraphicDefinitionMessage> implements Settable<SCS2YoGraphicDefinitionMessage>, EpsilonComparable<SCS2YoGraphicDefinitionMessage>
{
   public java.lang.StringBuilder type_;
   public java.lang.StringBuilder name_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage>  colors_;
   public us.ihmc.idl.IDLSequence.StringBuilderHolder  fieldNames_;
   public us.ihmc.idl.IDLSequence.StringBuilderHolder  fieldValues_;

   public SCS2YoGraphicDefinitionMessage()
   {
      type_ = new java.lang.StringBuilder(255);
      name_ = new java.lang.StringBuilder(255);
      colors_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage> (4, new us.ihmc.robotDataLogger.SCS2PaintDefinitionMessagePubSubType());
      fieldNames_ = new us.ihmc.idl.IDLSequence.StringBuilderHolder (64, "type_d");
      fieldValues_ = new us.ihmc.idl.IDLSequence.StringBuilderHolder (64, "type_d");

   }

   public SCS2YoGraphicDefinitionMessage(SCS2YoGraphicDefinitionMessage other)
   {
      this();
      set(other);
   }

   public void set(SCS2YoGraphicDefinitionMessage other)
   {
      type_.setLength(0);
      type_.append(other.type_);

      name_.setLength(0);
      name_.append(other.name_);

      colors_.set(other.colors_);
      fieldNames_.set(other.fieldNames_);
      fieldValues_.set(other.fieldValues_);
   }

   public void setType(java.lang.String type)
   {
      type_.setLength(0);
      type_.append(type);
   }

   public java.lang.String getTypeAsString()
   {
      return getType().toString();
   }
   public java.lang.StringBuilder getType()
   {
      return type_;
   }

   public void setName(java.lang.String name)
   {
      name_.setLength(0);
      name_.append(name);
   }

   public java.lang.String getNameAsString()
   {
      return getName().toString();
   }
   public java.lang.StringBuilder getName()
   {
      return name_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage>  getColors()
   {
      return colors_;
   }


   public us.ihmc.idl.IDLSequence.StringBuilderHolder  getFieldNames()
   {
      return fieldNames_;
   }


   public us.ihmc.idl.IDLSequence.StringBuilderHolder  getFieldValues()
   {
      return fieldValues_;
   }


   public static Supplier<SCS2YoGraphicDefinitionMessagePubSubType> getPubSubType()
   {
      return SCS2YoGraphicDefinitionMessagePubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return SCS2YoGraphicDefinitionMessagePubSubType::new;
   }

   @Override
   public boolean epsilonEquals(SCS2YoGraphicDefinitionMessage other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.type_, other.type_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.name_, other.name_, epsilon)) return false;

      if (this.colors_.size() != other.colors_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.colors_.size(); i++)
         {  if (!this.colors_.get(i).epsilonEquals(other.colors_.get(i), epsilon)) return false; }
      }

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilderSequence(this.fieldNames_, other.fieldNames_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilderSequence(this.fieldValues_, other.fieldValues_, epsilon)) return false;


      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof SCS2YoGraphicDefinitionMessage)) return false;

      SCS2YoGraphicDefinitionMessage otherMyClass = (SCS2YoGraphicDefinitionMessage) other;

      if (!us.ihmc.idl.IDLTools.equals(this.type_, otherMyClass.type_)) return false;

      if (!us.ihmc.idl.IDLTools.equals(this.name_, otherMyClass.name_)) return false;

      if (!this.colors_.equals(otherMyClass.colors_)) return false;
      if (!this.fieldNames_.equals(otherMyClass.fieldNames_)) return false;
      if (!this.fieldValues_.equals(otherMyClass.fieldValues_)) return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("SCS2YoGraphicDefinitionMessage {");
      builder.append("type=");
      builder.append(this.type_);      builder.append(", ");
      builder.append("name=");
      builder.append(this.name_);      builder.append(", ");
      builder.append("colors=");
      builder.append(this.colors_);      builder.append(", ");
      builder.append("fieldNames=");
      builder.append(this.fieldNames_);      builder.append(", ");
      builder.append("fieldValues=");
      builder.append(this.fieldValues_);
      builder.append("}");
      return builder.toString();
   }
}
