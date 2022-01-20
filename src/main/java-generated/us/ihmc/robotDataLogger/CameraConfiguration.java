package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class CameraConfiguration extends Packet<CameraConfiguration> implements Settable<CameraConfiguration>, EpsilonComparable<CameraConfiguration>
{
   public us.ihmc.robotDataLogger.CameraType type_;
   public byte camera_id_;
   public java.lang.StringBuilder name_;
   public java.lang.StringBuilder identifier_;

   public CameraConfiguration()
   {
      name_ = new java.lang.StringBuilder(255);
      identifier_ = new java.lang.StringBuilder(255);
   }

   public CameraConfiguration(CameraConfiguration other)
   {
      this();
      set(other);
   }

   public void set(CameraConfiguration other)
   {
      type_ = other.type_;

      camera_id_ = other.camera_id_;

      name_.setLength(0);
      name_.append(other.name_);

      identifier_.setLength(0);
      identifier_.append(other.identifier_);

   }

   public void setType(us.ihmc.robotDataLogger.CameraType type)
   {
      type_ = type;
   }
   public us.ihmc.robotDataLogger.CameraType getType()
   {
      return type_;
   }

   public void setCameraId(byte camera_id)
   {
      camera_id_ = camera_id;
   }
   public byte getCameraId()
   {
      return camera_id_;
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

   public void setIdentifier(java.lang.String identifier)
   {
      identifier_.setLength(0);
      identifier_.append(identifier);
   }

   public java.lang.String getIdentifierAsString()
   {
      return getIdentifier().toString();
   }
   public java.lang.StringBuilder getIdentifier()
   {
      return identifier_;
   }


   public static Supplier<CameraConfigurationPubSubType> getPubSubType()
   {
      return CameraConfigurationPubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return CameraConfigurationPubSubType::new;
   }

   @Override
   public boolean epsilonEquals(CameraConfiguration other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsEnum(this.type_, other.type_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.camera_id_, other.camera_id_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.name_, other.name_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.identifier_, other.identifier_, epsilon)) return false;


      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof CameraConfiguration)) return false;

      CameraConfiguration otherMyClass = (CameraConfiguration) other;

      if(this.type_ != otherMyClass.type_) return false;

      if(this.camera_id_ != otherMyClass.camera_id_) return false;

      if (!us.ihmc.idl.IDLTools.equals(this.name_, otherMyClass.name_)) return false;

      if (!us.ihmc.idl.IDLTools.equals(this.identifier_, otherMyClass.identifier_)) return false;


      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("CameraConfiguration {");
      builder.append("type=");
      builder.append(this.type_);      builder.append(", ");
      builder.append("camera_id=");
      builder.append(this.camera_id_);      builder.append(", ");
      builder.append("name=");
      builder.append(this.name_);      builder.append(", ");
      builder.append("identifier=");
      builder.append(this.identifier_);
      builder.append("}");
      return builder.toString();
   }
}
