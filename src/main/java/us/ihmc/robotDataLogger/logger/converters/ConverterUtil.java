package us.ihmc.robotDataLogger.logger.converters;

import logger_msgs.msg.dds.HandshakeFileType;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ConverterUtil
{
   static YoVariableHandshakeParser getHandshake(HandshakeFileType type, File handshake) throws IOException
   {
      if (!handshake.exists())
      {
         throw new RuntimeException("Cannot find " + handshake);
      }

      DataInputStream handshakeStream = new DataInputStream(new FileInputStream(handshake));
      byte[] handshakeData = new byte[(int) handshake.length()];
      handshakeStream.readFully(handshakeData);
      handshakeStream.close();

      YoVariableHandshakeParser parser = YoVariableHandshakeParser.create(type);
      parser.parseFrom(handshakeData);
      return parser;
   }
}
