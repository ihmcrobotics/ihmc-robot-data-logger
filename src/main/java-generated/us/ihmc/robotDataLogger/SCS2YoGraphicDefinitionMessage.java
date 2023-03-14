package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class SCS2YoGraphicDefinitionMessage extends Packet<SCS2YoGraphicDefinitionMessage> implements Settable<SCS2YoGraphicDefinitionMessage>, EpsilonComparable<SCS2YoGraphicDefinitionMessage>
{
   public us.ihmc.idl.IDLSequence.StringBuilderHolder  fieldNames_;
   public us.ihmc.idl.IDLSequence.StringBuilderHolder  fieldValues_;

   public SCS2YoGraphicDefinitionMessage()
   {
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
      fieldNames_.set(other.fieldNames_);
      fieldValues_.set(other.fieldValues_);
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

      if (!this.fieldNames_.equals(otherMyClass.fieldNames_)) return false;
      if (!this.fieldValues_.equals(otherMyClass.fieldValues_)) return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("SCS2YoGraphicDefinitionMessage {");
      builder.append("fieldNames=");
      builder.append(this.fieldNames_);      builder.append(", ");
      builder.append("fieldValues=");
      builder.append(this.fieldValues_);
      builder.append("}");
      return builder.toString();
   }
}
