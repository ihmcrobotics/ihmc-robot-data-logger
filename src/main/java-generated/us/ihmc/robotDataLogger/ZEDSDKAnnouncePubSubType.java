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
   		return "9f246f58e62fc3a5fa946f596f47987112d8e383291e0da8503af3ec09242643";
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
      current_alignment += 2 + us.ihmc.idl.CDR.alignment(current_alignment, 2);


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getAddress().length() + 1;

      current_alignment += 2 + us.ihmc.idl.CDR.alignment(current_alignment, 2);



      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      if(data.getAddress().length() <= 255)
      cdr.write_type_d(data.getAddress());else
          throw new RuntimeException("address field exceeds the maximum length");

      cdr.write_type_1(data.getPort());

   }

   public static void read(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.CDR cdr)
   {
      cdr.read_type_d(data.getAddress());	
      data.setPort(cdr.read_type_1());
      	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.ZEDSDKAnnounce data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_d("address", data.getAddress());
      ser.write_type_1("port", data.getPort());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.ZEDSDKAnnounce data)
   {
      ser.read_type_d("address", data.getAddress());
      data.setPort(ser.read_type_1("port"));
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
