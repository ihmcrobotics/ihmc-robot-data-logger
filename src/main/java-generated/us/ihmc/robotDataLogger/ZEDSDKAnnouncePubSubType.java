package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "ZEDSDKAnnounce" defined in "ZEDSDK.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from ZEDSDK.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit ZEDSDK.idl instead.
*
*/
public class ZEDSDKAnnouncePubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.ZEDSDKAnnounce>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::ZEDSDKAnnounce";
   
   @Override
   public final java.lang.String getDefinitionChecksum()
   {
   		return "0a8a4b3b0580df7906673f4755a2db962b6a9a5b2be9db40bac836b844922163";
   }
   
   @Override
   public final java.lang.String getDefinitionVersion()
   {
   		return "local";
   }

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.ZEDSDKAnnounce data) throws java.io.IOException
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

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;
      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;
      current_alignment += 2 + us.ihmc.idl.CDR.alignment(current_alignment, 2);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getSensorName().length() + 1;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getAddress().length() + 1;

      current_alignment += 2 + us.ihmc.idl.CDR.alignment(current_alignment, 2);


      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);


      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);



      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      if(data.getSensorName().length() <= 255)
      cdr.write_type_d(data.getSensorName());else
          throw new RuntimeException("sensor_name field exceeds the maximum length: %d > %d".formatted(data.getSensorName().length(), 255));

      if(data.getAddress().length() <= 255)
      cdr.write_type_d(data.getAddress());else
          throw new RuntimeException("address field exceeds the maximum length: %d > %d".formatted(data.getAddress().length(), 255));

      cdr.write_type_1(data.getPort());

      cdr.write_type_2(data.getFps());

      cdr.write_type_2(data.getBitrate());

   }

   public static void read(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      cdr.read_type_d(data.getSensorName());	
      cdr.read_type_d(data.getAddress());	
      data.setPort(cdr.read_type_1());
      	
      data.setFps(cdr.read_type_2());
      	
      data.setBitrate(cdr.read_type_2());
      	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_d("sensor_name", data.getSensorName());
      ser.write_type_d("address", data.getAddress());
      ser.write_type_1("port", data.getPort());
      ser.write_type_2("fps", data.getFps());
      ser.write_type_2("bitrate", data.getBitrate());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.ZEDSDKAnnounce data)
   {
      ser.read_type_d("sensor_name", data.getSensorName());
      ser.read_type_d("address", data.getAddress());
      data.setPort(ser.read_type_1("port"));
      data.setFps(ser.read_type_2("fps"));
      data.setBitrate(ser.read_type_2("bitrate"));
   }

   public static void staticCopy(us.ihmc.robotDataLogger.ZEDSDKAnnounce src, us.ihmc.robotDataLogger.ZEDSDKAnnounce dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.ZEDSDKAnnounce createData()
   {
      return new us.ihmc.robotDataLogger.ZEDSDKAnnounce();
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
   
   public void serialize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.ZEDSDKAnnounce src, us.ihmc.robotDataLogger.ZEDSDKAnnounce dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public ZEDSDKAnnouncePubSubType newInstance()
   {
      return new ZEDSDKAnnouncePubSubType();
   }
}
