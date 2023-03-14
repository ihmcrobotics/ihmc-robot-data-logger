package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "SCS2PaintDefinitionMessage" defined in "Handshake.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from Handshake.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit Handshake.idl instead.
*
*/
public class SCS2PaintDefinitionMessagePubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::SCS2PaintDefinitionMessage";

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data) throws java.io.IOException
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
      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);for(int i0 = 0; i0 < 16; ++i0)
      {
        current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;
      }
      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);for(int i0 = 0; i0 < 16; ++i0)
      {
        current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + 255 + 1;
      }

      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getType().length() + 1;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      for(int i0 = 0; i0 < data.getFieldNames().size(); ++i0)
      {
          current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getFieldNames().get(i0).length() + 1;
      }
      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      for(int i0 = 0; i0 < data.getFieldValues().size(); ++i0)
      {
          current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getFieldValues().get(i0).length() + 1;
      }

      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      if(data.getType().length() <= 255)
      cdr.write_type_d(data.getType());else
          throw new RuntimeException("type field exceeds the maximum length");

      if(data.getFieldNames().size() <= 16)
      cdr.write_type_e(data.getFieldNames());else
          throw new RuntimeException("fieldNames field exceeds the maximum length");

      if(data.getFieldValues().size() <= 16)
      cdr.write_type_e(data.getFieldValues());else
          throw new RuntimeException("fieldValues field exceeds the maximum length");

   }

   public static void read(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      cdr.read_type_d(data.getType());	
      cdr.read_type_e(data.getFieldNames());	
      cdr.read_type_e(data.getFieldValues());	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_d("type", data.getType());
      ser.write_type_e("fieldNames", data.getFieldNames());
      ser.write_type_e("fieldValues", data.getFieldValues());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data)
   {
      ser.read_type_d("type", data.getType());
      ser.read_type_e("fieldNames", data.getFieldNames());
      ser.read_type_e("fieldValues", data.getFieldValues());
   }

   public static void staticCopy(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage src, us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage createData()
   {
      return new us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage();
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
   
   public void serialize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage src, us.ihmc.robotDataLogger.SCS2PaintDefinitionMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public SCS2PaintDefinitionMessagePubSubType newInstance()
   {
      return new SCS2PaintDefinitionMessagePubSubType();
   }
}
