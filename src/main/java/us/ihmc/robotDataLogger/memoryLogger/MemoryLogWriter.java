package us.ihmc.robotDataLogger.memoryLogger;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import us.ihmc.robotDataLogger.Announcement;
import us.ihmc.robotDataLogger.HandshakeFileType;
import us.ihmc.robotDataLogger.handshake.IDLYoVariableHandshakeParser;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerListener;
import us.ihmc.robotDataLogger.websocket.server.DataServerServerContent;

public class MemoryLogWriter
{
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
         logHandshake.setResourceDirectories(announcement.getModelFileDescription().getResourceDirectories().toStringArray());
         
         if (announcement.getModelFileDescription().getHasResourceZip())
         {
            logHandshake.setResourceZip(content.getResourceZip().array());
         }
      }
     
      IDLYoVariableHandshakeParser handshakeParser = new IDLYoVariableHandshakeParser(HandshakeFileType.IDL_YAML);
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
