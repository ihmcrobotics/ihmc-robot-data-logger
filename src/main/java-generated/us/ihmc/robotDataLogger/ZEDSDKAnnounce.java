package us.ihmc.robotDataLogger;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;
import java.util.function.Supplier;
import us.ihmc.pubsub.TopicDataType;

public class ZEDSDKAnnounce extends Packet<ZEDSDKAnnounce> implements Settable<ZEDSDKAnnounce>, EpsilonComparable<ZEDSDKAnnounce>
{
   public java.lang.StringBuilder sensor_name_;
   public int depthMode_;
   public java.lang.StringBuilder address_;
   public short port_;
   public int fps_;
   public int bitrate_;
   public long sensorTimestamp_;
   public long controllerTimestamp_;

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

      depthMode_ = other.depthMode_;

      address_.setLength(0);
      address_.append(other.address_);

      port_ = other.port_;

      fps_ = other.fps_;

      bitrate_ = other.bitrate_;

      sensorTimestamp_ = other.sensorTimestamp_;

      controllerTimestamp_ = other.controllerTimestamp_;

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

   public void setDepthMode(int depthMode)
   {
      depthMode_ = depthMode;
   }
   public int getDepthMode()
   {
      return depthMode_;
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

   public void setFps(int fps)
   {
      fps_ = fps;
   }
   public int getFps()
   {
      return fps_;
   }

   public void setBitrate(int bitrate)
   {
      bitrate_ = bitrate;
   }
   public int getBitrate()
   {
      return bitrate_;
   }

   public void setSensorTimestamp(long sensorTimestamp)
   {
      sensorTimestamp_ = sensorTimestamp;
   }
   public long getSensorTimestamp()
   {
      return sensorTimestamp_;
   }

   public void setControllerTimestamp(long controllerTimestamp)
   {
      controllerTimestamp_ = controllerTimestamp;
   }
   public long getControllerTimestamp()
   {
      return controllerTimestamp_;
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

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.depthMode_, other.depthMode_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsStringBuilder(this.address_, other.address_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.port_, other.port_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.fps_, other.fps_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.bitrate_, other.bitrate_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.sensorTimestamp_, other.sensorTimestamp_, epsilon)) return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.controllerTimestamp_, other.controllerTimestamp_, epsilon)) return false;


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

      if(this.depthMode_ != otherMyClass.depthMode_) return false;

      if (!us.ihmc.idl.IDLTools.equals(this.address_, otherMyClass.address_)) return false;

      if(this.port_ != otherMyClass.port_) return false;

      if(this.fps_ != otherMyClass.fps_) return false;

      if(this.bitrate_ != otherMyClass.bitrate_) return false;

      if(this.sensorTimestamp_ != otherMyClass.sensorTimestamp_) return false;

      if(this.controllerTimestamp_ != otherMyClass.controllerTimestamp_) return false;


      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("ZEDSDKAnnounce {");
      builder.append("sensor_name=");
      builder.append(this.sensor_name_);      builder.append(", ");
      builder.append("depthMode=");
      builder.append(this.depthMode_);      builder.append(", ");
      builder.append("address=");
      builder.append(this.address_);      builder.append(", ");
      builder.append("port=");
      builder.append(this.port_);      builder.append(", ");
      builder.append("fps=");
      builder.append(this.fps_);      builder.append(", ");
      builder.append("bitrate=");
      builder.append(this.bitrate_);      builder.append(", ");
      builder.append("sensorTimestamp=");
      builder.append(this.sensorTimestamp_);      builder.append(", ");
      builder.append("controllerTimestamp=");
      builder.append(this.controllerTimestamp_);
      builder.append("}");
      return builder.toString();
   }
}
