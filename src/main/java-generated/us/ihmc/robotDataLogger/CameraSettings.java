package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class CameraSettings extends Packet<CameraSettings> implements Settable<CameraSettings>, EpsilonComparable<CameraSettings>
{
   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.CameraConfiguration>  cameras_;

   public CameraSettings()
   {
      cameras_ = new us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.CameraConfiguration> (128, new us.ihmc.robotDataLogger.CameraConfigurationPubSubType());

   }

   public CameraSettings(CameraSettings other)
   {
      this();
      set(other);
   }

   public void set(CameraSettings other)
   {
      cameras_.set(other.cameras_);
   }


   public us.ihmc.idl.IDLSequence.Object<us.ihmc.robotDataLogger.CameraConfiguration>  getCameras()
   {
      return cameras_;
   }


   public static Supplier<CameraSettingsPubSubType> getPubSubType()
   {
      return CameraSettingsPubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return CameraSettingsPubSubType::new;
   }

   @Override
   public boolean epsilonEquals(CameraSettings other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (this.cameras_.size() != other.cameras_.size()) { return false; }
      else
      {
         for (int i = 0; i < this.cameras_.size(); i++)
         {  if (!this.cameras_.get(i).epsilonEquals(other.cameras_.get(i), epsilon)) return false; }
      }

      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof CameraSettings)) return false;

      CameraSettings otherMyClass = (CameraSettings) other;

      if (!this.cameras_.equals(otherMyClass.cameras_)) return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("CameraSettings {");
      builder.append("cameras=");
      builder.append(this.cameras_);
      builder.append("}");
      return builder.toString();
   }
}
