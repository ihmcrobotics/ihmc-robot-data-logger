package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "SCS1YoGraphicObjectMessage" defined in "Handshake.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from Handshake.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit Handshake.idl instead.
*
*/
public class SCS1YoGraphicObjectMessagePubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::SCS1YoGraphicObjectMessage";
   
   @Override
   public final java.lang.String getDefinitionChecksum()
   {
   		return "80e80cd3d776326ea346e58b62baf63b391b44f4d9d346f21c105f4dcb93c7e9";
   }
   
   @Override
   public final java.lang.String getDefinitionVersion()
   {
   		return "local";
   }

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data) throws java.io.IOException
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

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;
      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);current_alignment += (1024 * 2) + us.ihmc.idl.CDR.alignment(current_alignment, 2);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);current_alignment += (128 * 8) + us.ihmc.idl.CDR.alignment(current_alignment, 8);

      current_alignment += us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType.getMaxCdrSerializedSize(current_alignment);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;

      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);


      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getName().length() + 1;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      current_alignment += (data.getYoVariableIndex().size() * 2) + us.ihmc.idl.CDR.alignment(current_alignment, 2);


      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      current_alignment += (data.getConstants().size() * 8) + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      current_alignment += us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType.getCdrSerializedSize(data.getAppearance(), current_alignment);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getListName().length() + 1;


      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.idl.CDR cdr)
   {
      cdr.write_type_2(data.getRegistrationID());

      if(data.getName().length() <= 255)
      cdr.write_type_d(data.getName());else
          throw new RuntimeException("name field exceeds the maximum length");

      if(data.getYoVariableIndex().size() <= 1024)
      cdr.write_type_e(data.getYoVariableIndex());else
          throw new RuntimeException("yoVariableIndex field exceeds the maximum length");

      if(data.getConstants().size() <= 128)
      cdr.write_type_e(data.getConstants());else
          throw new RuntimeException("constants field exceeds the maximum length");

      us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType.write(data.getAppearance(), cdr);
      if(data.getListName().length() <= 255)
      cdr.write_type_d(data.getListName());else
          throw new RuntimeException("listName field exceeds the maximum length");

   }

   public static void read(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.idl.CDR cdr)
   {
      data.setRegistrationID(cdr.read_type_2());
      	
      cdr.read_type_d(data.getName());	
      cdr.read_type_e(data.getYoVariableIndex());	
      cdr.read_type_e(data.getConstants());	
      us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType.read(data.getAppearance(), cdr);	
      cdr.read_type_d(data.getListName());	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_2("registrationID", data.getRegistrationID());
      ser.write_type_d("name", data.getName());
      ser.write_type_e("yoVariableIndex", data.getYoVariableIndex());
      ser.write_type_e("constants", data.getConstants());
      ser.write_type_a("appearance", new us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType(), data.getAppearance());

      ser.write_type_d("listName", data.getListName());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data)
   {
      data.setRegistrationID(ser.read_type_2("registrationID"));
      ser.read_type_d("name", data.getName());
      ser.read_type_e("yoVariableIndex", data.getYoVariableIndex());
      ser.read_type_e("constants", data.getConstants());
      ser.read_type_a("appearance", new us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessagePubSubType(), data.getAppearance());

      ser.read_type_d("listName", data.getListName());
   }

   public static void staticCopy(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage src, us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage createData()
   {
      return new us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage();
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
   
   public void serialize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage src, us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public SCS1YoGraphicObjectMessagePubSubType newInstance()
   {
      return new SCS1YoGraphicObjectMessagePubSubType();
   }
}
