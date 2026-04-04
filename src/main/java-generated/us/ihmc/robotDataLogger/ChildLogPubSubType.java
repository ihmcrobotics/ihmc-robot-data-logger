package us.ihmc.robotDataLogger;

/**
* 
* Topic data type of the struct "ChildLog" defined in "LogProperties.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from LogProperties.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit LogProperties.idl instead.
*
*/
public class ChildLogPubSubType implements us.ihmc.pubsub.TopicDataType<us.ihmc.robotDataLogger.ChildLog>
{
   public static final java.lang.String name = "us::ihmc::robotDataLogger::ChildLog";
   
   @Override
   public final java.lang.String getDefinitionChecksum()
   {
   		return "33f5dfe9eb2f90731e97a3c905c6ecb7ec6cf77d8937c09ddd975d85b4e26fb6";
   }
   
   @Override
   public final java.lang.String getDefinitionVersion()
   {
   		return "local";
   }

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, us.ihmc.robotDataLogger.ChildLog data) throws java.io.IOException
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
      current_alignment += us.ihmc.robotDataLogger.SynchronizationPubSubType.getMaxCdrSerializedSize(current_alignment);


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ChildLog data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(us.ihmc.robotDataLogger.ChildLog data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4) + data.getChildName().length() + 1;

      current_alignment += us.ihmc.robotDataLogger.SynchronizationPubSubType.getCdrSerializedSize(data.getSynchronization(), current_alignment);


      return current_alignment - initial_alignment;
   }

   public static void write(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.idl.CDR cdr)
   {
      if(data.getChildName().length() <= 255)
      cdr.write_type_d(data.getChildName());else
          throw new RuntimeException("childName field exceeds the maximum length: %d > %d".formatted(data.getChildName().length(), 255));

      us.ihmc.robotDataLogger.SynchronizationPubSubType.write(data.getSynchronization(), cdr);
   }

   public static void read(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.idl.CDR cdr)
   {
      cdr.read_type_d(data.getChildName());	
      us.ihmc.robotDataLogger.SynchronizationPubSubType.read(data.getSynchronization(), cdr);	

   }

   @Override
   public final void serialize(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_d("childName", data.getChildName());
      ser.write_type_a("synchronization", new us.ihmc.robotDataLogger.SynchronizationPubSubType(), data.getSynchronization());

   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, us.ihmc.robotDataLogger.ChildLog data)
   {
      ser.read_type_d("childName", data.getChildName());
      ser.read_type_a("synchronization", new us.ihmc.robotDataLogger.SynchronizationPubSubType(), data.getSynchronization());

   }

   public static void staticCopy(us.ihmc.robotDataLogger.ChildLog src, us.ihmc.robotDataLogger.ChildLog dest)
   {
      dest.set(src);
   }

   @Override
   public us.ihmc.robotDataLogger.ChildLog createData()
   {
      return new us.ihmc.robotDataLogger.ChildLog();
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
   
   public void serialize(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(us.ihmc.robotDataLogger.ChildLog data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(us.ihmc.robotDataLogger.ChildLog src, us.ihmc.robotDataLogger.ChildLog dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public ChildLogPubSubType newInstance()
   {
      return new ChildLogPubSubType();
   }
}
