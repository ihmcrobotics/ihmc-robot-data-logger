package us.ihmc.robotDataLogger.logger.converters;

import logger_msgs.HandshakeFileType;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ConverterUtil
{
   static YoVariableHandshakeParser getHandshake(HandshakeFileType type, File handshake) throws IOException
   {
      return getHandshake(type.getType(), handshake);
   }

   static YoVariableHandshakeParser getHandshake(byte handshakeFileType, File handshake) throws IOException
   {
      if (!handshake.exists())
      {
         throw new RuntimeException("Cannot find " + handshake);
      }

      DataInputStream handshakeStream = new DataInputStream(new FileInputStream(handshake));
      byte[] handshakeData = new byte[(int) handshake.length()];
      handshakeStream.readFully(handshakeData);
      handshakeStream.close();

      YoVariableHandshakeParser parser = YoVariableHandshakeParser.create(handshakeFileType);
      parser.parseFrom(handshakeData);
      return parser;
   }
}
