package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "SCS1AppearanceDefinitionMessage" defined in "Handshake.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from Handshake.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit Handshake.idl instead.
*
*/
public class SCS1AppearanceDefinitionMessagePubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::SCS1AppearanceDefinitionMessage";
   
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
   public void serialize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data) throws java.io.IOException
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

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);



      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      cdr.write_type_6(data.getR());

      cdr.write_type_6(data.getG());

      cdr.write_type_6(data.getB());

      cdr.write_type_6(data.getTransparency());

   }

   public static void read(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      data.setR(cdr.read_type_6());
      	
      data.setG(cdr.read_type_6());
      	
      data.setB(cdr.read_type_6());
      	
      data.setTransparency(cdr.read_type_6());
      	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_6("r", data.getR());
      ser.write_type_6("g", data.getG());
      ser.write_type_6("b", data.getB());
      ser.write_type_6("transparency", data.getTransparency());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data)
   {
      data.setR(ser.read_type_6("r"));
      data.setG(ser.read_type_6("g"));
      data.setB(ser.read_type_6("b"));
      data.setTransparency(ser.read_type_6("transparency"));
   }

   public static void staticCopy(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage src, us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage createData()
   {
      return new us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage();
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
   
   public void serialize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage src, us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public SCS1AppearanceDefinitionMessagePubSubType newInstance()
   {
      return new SCS1AppearanceDefinitionMessagePubSubType();
   }
}
