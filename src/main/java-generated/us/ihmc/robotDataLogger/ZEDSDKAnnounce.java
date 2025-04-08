package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class ZEDSDKAnnounce extends Packet<ZEDSDKAnnounce> implements Settable<ZEDSDKAnnounce>, EpsilonComparable<ZEDSDKAnnounce>
{
   public java.lang.StringBuilder sensor_name_;
   public java.lang.StringBuilder address_;
   public short port_;

   public ZEDSDKAnnounce()
   {
      sensor_name_ = new java.lang.StringBuilder(255);
      address_ = new java.lang.StringBuilder(255);
   }

   public ZEDSDKAnnounce(ZEDSDKAnnounce other)
   {
      this();
      set(other);
   }

   public void set(ZEDSDKAnnounce other)
   {
      sensor_name_.setLength(0);
      sensor_name_.append(other.sensor_name_);

      address_.setLength(0);
      address_.append(other.address_);

      port_ = other.port_;

   }

   public void setSensorName(java.lang.String sensor_name)
   {
      sensor_name_.setLength(0);
      sensor_name_.append(sensor_name);
   }

   public java.lang.String getSensorNameAsString()
   {
      return getSensorName().toString();
   }
   public java.lang.StringBuilder getSensorName()
   {
      return sensor_name_;
   }

   public void setAddress(java.lang.String address)
   {
      address_.setLength(0);
      address_.append(address);
   }

   public java.lang.String getAddressAsString()
   {
      return getAddress().toString();
   }
   public java.lang.StringBuilder getAddress()
   {
      return address_;
   }

   public void setPort(short port)
   {
      port_ = port;
   }
   public short getPort()
   {
      return port_;
   }


   public static Supplier<ZEDSDKAnnouncePubSubType> getPubSubType()
   {
      return ZEDSDKAnnouncePubSubType::new;
   }

   @Override
   public Supplier<TopicDataType> getPubSubTypePacket()
   {
      return ZEDSDKAnnouncePubSubType::new;
   }

   @Override
   public boolean epsilonEquals(ZEDSDKAnnounce other, double epsilon)
   {
      if(other == null) return false;
      if(other == this) return true;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.sensor_name_, other.sensor_name_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.address_, other.address_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.port_, other.port_, epsilon)) return false;


      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if(other == null) return false;
      if(other == this) return true;
      if(!(other instanceof ZEDSDKAnnounce)) return false;

      ZEDSDKAnnounce otherMyClass = (ZEDSDKAnnounce) other;

      if (!us.ihmc.idl.IDLTools.equals(this.sensor_name_, otherMyClass.sensor_name_)) return false;

      if (!us.ihmc.idl.IDLTools.equals(this.address_, otherMyClass.address_)) return false;

      if(this.port_ != otherMyClass.port_) return false;


      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("ZEDSDKAnnounce {");
      builder.append("sensor_name=");
      builder.append(this.sensor_name_);      builder.append(", ");
      builder.append("address=");
      builder.append(this.address_);      builder.append(", ");
      builder.append("port=");
      builder.append(this.port_);
      builder.append("}");
      return builder.toString();
   }
}
