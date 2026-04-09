package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "Synchronization" defined in "LogProperties.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from LogProperties.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit LogProperties.idl instead.
*
*/
public class SynchronizationPubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.Synchronization>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::Synchronization";
   
   @Override
   public final java.lang.String getDefinitionChecksum()
   {
   		return "409da809df88ed676d55bc70ef65fa47757edb9806850332ae504ddbf53e2d6a";
   }
   
   @Override
   public final java.lang.String getDefinitionVersion()
   {
   		return "local";
   }

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.Synchronization data) throws java.io.IOException
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


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.Synchronization data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.Synchronization data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);


      current_alignment += 8 + us.ihmc.idl.CDR.alignment(current_alignment, 8);



      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.idl.CDR cdr)
   {
      cdr.write_type_6(data.getOffset());

      cdr.write_type_6(data.getJogRate());

   }

   public static void read(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.idl.CDR cdr)
   {
      data.setOffset(cdr.read_type_6());
      	
      data.setJogRate(cdr.read_type_6());
      	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_6("offset", data.getOffset());
      ser.write_type_6("jogRate", data.getJogRate());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.Synchronization data)
   {
      data.setOffset(ser.read_type_6("offset"));
      data.setJogRate(ser.read_type_6("jogRate"));
   }

   public static void staticCopy(us.ihmc.robotDataLogger.Synchronization src, us.ihmc.robotDataLogger.Synchronization dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.Synchronization createData()
   {
      return new us.ihmc.robotDataLogger.Synchronization();
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
   
   public void serialize(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.Synchronization data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.Synchronization src, us.ihmc.robotDataLogger.Synchronization dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public SynchronizationPubSubType newInstance()
   {
      return new SynchronizationPubSubType();
   }
}
