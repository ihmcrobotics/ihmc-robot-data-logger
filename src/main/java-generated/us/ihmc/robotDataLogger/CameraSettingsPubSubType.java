package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "CameraSettings" defined in "CameraSettings.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from CameraSettings.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit CameraSettings.idl instead.
*
*/
public class CameraSettingsPubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.CameraSettings>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::CameraSettings";

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.CameraSettings data) throws java.io.IOException
   {
      deserializeCDR.deserialize(serializedPayload);
      read(data, deserializeCDR);
      deserializeCDR.finishDeserialize();
   }

   public static int getMaxCdrSerializedSize()
   {
      return getMaxCdrSerializedSize(0);
   }

   public static int getMaxCdrSerializedSize(int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);for(int i0 = 0; i0 < 128; ++i0)
      {
          current_alignment += us.ihmc.robotDataLogger.CameraConfigurationPubSubType.getMaxCdrSerializedSize(current_alignment);}
      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.CameraSettings data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.CameraSettings data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      for(int i0 = 0; i0 < data.getCameras().size(); ++i0)
      {
          current_alignment += us.ihmc.robotDataLogger.CameraConfigurationPubSubType.getCdrSerializedSize(data.getCameras().get(i0), current_alignment);}

      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.idl.CDR cdr)
   {
      if(data.getCameras().size() <= 128)
      cdr.write_type_e(data.getCameras());else
          throw new RuntimeException("cameras field exceeds the maximum length");

   }

   public static void read(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.idl.CDR cdr)
   {
      cdr.read_type_e(data.getCameras());	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_e("cameras", data.getCameras());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.CameraSettings data)
   {
      ser.read_type_e("cameras", data.getCameras());
   }

   public static void staticCopy(us.ihmc.robotDataLogger.CameraSettings src, us.ihmc.robotDataLogger.CameraSettings dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.CameraSettings createData()
   {
      return new us.ihmc.robotDataLogger.CameraSettings();
   }
   @Override
   public int getTypeSize()
   {
      return us.ihmc.idl.CDR.getTypeSize(getMaxCdrSerializedSize());
   }

   @Override
   public java.lang.String getName()
   {
      return name;
   }
   
   public void serialize(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.CameraSettings data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.CameraSettings src, us.ihmc.robotDataLogger.CameraSettings dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public CameraSettingsPubSubType newInstance()
   {
      return new CameraSettingsPubSubType();
   }
}
