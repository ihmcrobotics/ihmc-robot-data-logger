package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class Handshake extends Packet<Handshake> implements Settable<Handshake>, EpsilonComparable<Handshake>
{
   public double dt_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoRegistryDefinition>  registries_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoVariableDefinition>  variables_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.JointDefinition>  joints_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage>  graphicObjects_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage>  artifacts_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessage>  scs2YoGraphicDefinitions_;
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.EnumType>  enumTypes_;
   public us.ihmc.robotDataLogger.ReferenceFrameInformation referenceFrameInformation_;
   public us.ihmc.robotDataLogger.Summary summary_;

   public Handshake()
   {
      registries_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoRegistryDefinition> (1024, new us.ihmc.robotDataLogger.YoRegistryDefinitionPubSubType());
      variables_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoVariableDefinition> (32767, new us.ihmc.robotDataLogger.YoVariableDefinitionPubSubType());
      joints_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.JointDefinition> (128, new us.ihmc.robotDataLogger.JointDefinitionPubSubType());
      graphicObjects_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage> (2048, new us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessagePubSubType());
      artifacts_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage> (2048, new us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessagePubSubType());
      scs2YoGraphicDefinitions_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessage> (2048, new us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessagePubSubType());
      enumTypes_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.EnumType> (1024, new us.ihmc.robotDataLogger.EnumTypePubSubType());
      referenceFrameInformation_ = new us.ihmc.robotDataLogger.ReferenceFrameInformation();
      summary_ = new us.ihmc.robotDataLogger.Summary();

   }

   public Handshake(Handshake other)
   {
      this();
      set(other);
   }

   public void set(Handshake other)
   {
      dt_ = other.dt_;

      registries_.set(other.registries_);
      variables_.set(other.variables_);
      joints_.set(other.joints_);
      graphicObjects_.set(other.graphicObjects_);
      artifacts_.set(other.artifacts_);
      scs2YoGraphicDefinitions_.set(other.scs2YoGraphicDefinitions_);
      enumTypes_.set(other.enumTypes_);
      us.ihmc.robotDataLogger.ReferenceFrameInformationPubSubType.staticCopy(other.referenceFrameInformation_, referenceFrameInformation_);
      us.ihmc.robotDataLogger.SummaryPubSubType.staticCopy(other.summary_, summary_);
   }

   public void setDt(double dt)
   {
      dt_ = dt;
   }
   public double getDt()
   {
      return dt_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoRegistryDefinition>  getRegistries()
   {
      return registries_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.YoVariableDefinition>  getVariables()
   {
      return variables_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.JointDefinition>  getJoints()
   {
      return joints_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage>  getGraphicObjects()
   {
      return graphicObjects_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage>  getArtifacts()
   {
      return artifacts_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessage>  getScs2YoGraphicDefinitions()
   {
      return scs2YoGraphicDefinitions_;
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.EnumType>  getEnumTypes()
   {
      return enumTypes_;
   }


   public us.ihmc.robotDataLogger.ReferenceFrameInformation getReferenceFrameInformation()
   {
      return referenceFrameInformation_;
   }


   public us.ihmc.robotDataLogger.Summary getSummary()
   {
      return summary_;
   }


   public static Supplier<HandshakePubSubType> getPubSubType()
   {
      return HandshakePubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return HandshakePubSubType::new;
   }

   @Override
   public boolean epsilonEquals(Handshake other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.dt_, other.dt_, epsilon)) return false;

      if (this.registries_.size() != other.registries_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.registries_.size(); i++)
         {  if (!this.registries_.get(i).epsilonEquals(other.registries_.get(i), epsilon)) return false; }
      }

      if (this.variables_.size() != other.variables_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.variables_.size(); i++)
         {  if (!this.variables_.get(i).epsilonEquals(other.variables_.get(i), epsilon)) return false; }
      }

      if (this.joints_.size() != other.joints_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.joints_.size(); i++)
         {  if (!this.joints_.get(i).epsilonEquals(other.joints_.get(i), epsilon)) return false; }
      }

      if (this.graphicObjects_.size() != other.graphicObjects_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.graphicObjects_.size(); i++)
         {  if (!this.graphicObjects_.get(i).epsilonEquals(other.graphicObjects_.get(i), epsilon)) return false; }
      }

      if (this.artifacts_.size() != other.artifacts_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.artifacts_.size(); i++)
         {  if (!this.artifacts_.get(i).epsilonEquals(other.artifacts_.get(i), epsilon)) return false; }
      }

      if (this.scs2YoGraphicDefinitions_.size() != other.scs2YoGraphicDefinitions_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.scs2YoGraphicDefinitions_.size(); i++)
         {  if (!this.scs2YoGraphicDefinitions_.get(i).epsilonEquals(other.scs2YoGraphicDefinitions_.get(i), epsilon)) return false; }
      }

      if (this.enumTypes_.size() != other.enumTypes_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.enumTypes_.size(); i++)
         {  if (!this.enumTypes_.get(i).epsilonEquals(other.enumTypes_.get(i), epsilon)) return false; }
      }

      if (!this.referenceFrameInformation_.epsilonEquals(other.referenceFrameInformation_, epsilon)) return false;
      if (!this.summary_.epsilonEquals(other.summary_, epsilon)) return false;

      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof Handshake)) return false;

      Handshake otherMyClass = (Handshake) other;

      if(this.dt_ != otherMyClass.dt_) return false;

      if (!this.registries_.equals(otherMyClass.registries_)) return false;
      if (!this.variables_.equals(otherMyClass.variables_)) return false;
      if (!this.joints_.equals(otherMyClass.joints_)) return false;
      if (!this.graphicObjects_.equals(otherMyClass.graphicObjects_)) return false;
      if (!this.artifacts_.equals(otherMyClass.artifacts_)) return false;
      if (!this.scs2YoGraphicDefinitions_.equals(otherMyClass.scs2YoGraphicDefinitions_)) return false;
      if (!this.enumTypes_.equals(otherMyClass.enumTypes_)) return false;
      if (!this.referenceFrameInformation_.equals(otherMyClass.referenceFrameInformation_)) return false;
      if (!this.summary_.equals(otherMyClass.summary_)) return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("Handshake {");
      builder.append("dt=");
      builder.append(this.dt_);      builder.append(", ");
      builder.append("registries=");
      builder.append(this.registries_);      builder.append(", ");
      builder.append("variables=");
      builder.append(this.variables_);      builder.append(", ");
      builder.append("joints=");
      builder.append(this.joints_);      builder.append(", ");
      builder.append("graphicObjects=");
      builder.append(this.graphicObjects_);      builder.append(", ");
      builder.append("artifacts=");
      builder.append(this.artifacts_);      builder.append(", ");
      builder.append("scs2YoGraphicDefinitions=");
      builder.append(this.scs2YoGraphicDefinitions_);      builder.append(", ");
      builder.append("enumTypes=");
      builder.append(this.enumTypes_);      builder.append(", ");
      builder.append("referenceFrameInformation=");
      builder.append(this.referenceFrameInformation_);      builder.append(", ");
      builder.append("summary=");
      builder.append(this.summary_);
      builder.append("}");
      return builder.toString();
   }
}
