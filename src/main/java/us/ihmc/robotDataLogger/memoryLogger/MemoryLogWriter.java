package us.ihmc.robotDataLogger.memoryLogger;

import logger_msgs.msg.dds.Announcement;
import logger_msgs.msg.dds.HandshakeFileType;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.robotDataLogger.handshake.IDLYoVariableHandshakeParser;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerListener;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MemoryLogWriter
{
   /**
    * Helper method to convert IDLStringSequence to String array
    */
   private static String[] toStringArray(IDLStringSequence sequence)
   {
      String[] result = new String[sequence.size()];
      for (int i = 0; i < sequence.size(); i++)
      {
         result[i] = sequence.getAsString(i);
      }
      return result;
   }
   private class MemoryLoggerListener extends YoVariableLoggerListener
   {
      private final ByteBuffer buffer;
      
      public MemoryLoggerListener(File tempDirectory, File finalDirectory, String timestamp, Announcement request, IDLYoVariableHandshakeParser handshakeParser)
      {
         super(tempDirectory, finalDirectory, timestamp, request);
         
         buffer = ByteBuffer.allocateDirect(handshakeParser.getBufferSize());
      }
      
      @Override
      protected ByteBuffer reconstructBuffer(long timestamp)
      {
         return buffer;
      }
      
      public void writeMemoryBufferEntry(MemoryBufferEntry entry)
      {
         buffer.clear();
         
         long timestamp = entry.getTimestamp();
         
         buffer.putLong(timestamp);
         
         for(int i = 0; i< entry.variables.length; i++)         
         {
            ByteBuffer variables = entry.variables[i];
            variables.clear();
            buffer.put(variables);
         }
         
         for(int i = 0; i < entry.jointStates.length; i++)
         {
            double[] jointStates = entry.jointStates[i];
            if(jointStates != null)
            {
               for(int j = 0; j < jointStates.length; j++)
               {
                  buffer.putDouble(jointStates[j]);
               }
            }
         }
         
         buffer.flip();
         
         super.receivedTimestampAndData(timestamp);
      }
   }
   
   /**
    * Hacky way to re-use the writer from the logger, to make sure the format is the same
    */
   private final MemoryLoggerListener listener;
   
   public MemoryLogWriter(DataServerServerContent content, File logDirectory)
   {
      Announcement announcement = content.getAnnouncementObject();

      DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      Calendar calendar = Calendar.getInstance();
      String timestamp = dateFormat.format(calendar.getTime());

      File tempDirectory = new File(logDirectory, "." + timestamp + "_" + announcement.getName());

      File finalDirectory = new File(logDirectory, timestamp + "_" + announcement.getName());
      if (finalDirectory.exists())
      {
         throw new RuntimeException("Directory " + finalDirectory.getAbsolutePath() + " already exists");
      }

      if (tempDirectory.exists())
      {
         throw new RuntimeException("Temp directory " + finalDirectory.getAbsolutePath() + " already exists");
      }
      if (!tempDirectory.mkdir())
      {
         throw new RuntimeException("Cannot create directory " + finalDirectory.getAbsolutePath());
      }
      
      LogHandshake logHandshake = new LogHandshake();
      
      String modelName = announcement.getModelFileDescription().getNameAsString();
      logHandshake.setModelName(modelName);
      logHandshake.setHandshake(content.getHandshakeObject());
      
      if (announcement.getModelFileDescription().getHasModel())
      {
         logHandshake.setModel(content.getModel().array());
         logHandshake.setModelLoaderClass(announcement.getModelFileDescription().getModelLoaderClassAsString());
         logHandshake.setResourceDirectories(toStringArray(announcement.getModelFileDescription().getResourceDirectories()));
         
         if (announcement.getModelFileDescription().getHasResourceZip())
         {
            logHandshake.setResourceZip(content.getResourceZip().array());
         }
      }

      HandshakeFileType handshakeFileType = new HandshakeFileType();
      handshakeFileType.setType(HandshakeFileType.IDL_YAML);
      IDLYoVariableHandshakeParser handshakeParser = new IDLYoVariableHandshakeParser(handshakeFileType);
      handshakeParser.parseFrom(content.getHandshakeObject());
      
      this.listener = new MemoryLoggerListener(tempDirectory, finalDirectory, timestamp, announcement, handshakeParser);
      this.listener.start(null, logHandshake, handshakeParser, null);
   }
   
   public void addBuffer(MemoryBufferEntry entry) 
   {
      this.listener.writeMemoryBufferEntry(entry);
   }
   
   public void finish()
   {
      this.listener.disconnected();
   }
}
