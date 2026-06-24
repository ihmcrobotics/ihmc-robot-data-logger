package logger_msgs;


/**
 * Helpers for ROS 2 enum messages generated as {@code uint8 type} wrappers.
 */
public final class MessageTypes
{
   public static final JointType ONEDOF_JOINT = jointType(JointType.ONEDOFJOINT);
   public static final JointType SIXDOF_JOINT = jointType(JointType.SIXDOFJOINT);

   public static final HandshakeFileType IDL_YAML = handshakeFileType(HandshakeFileType.IDL_YAML);
   public static final HandshakeFileType IDL_CDR = handshakeFileType(HandshakeFileType.IDL_CDR);

   public static final LogDataType DATA_PACKET = logDataType(LogDataType.DATA_PACKET);

   private MessageTypes()
   {
   }

   public static JointType jointType(byte type)
   {
      JointType jointType = new JointType();
      jointType.setType(type);
      return jointType;
   }

   public static HandshakeFileType handshakeFileType(byte type)
   {
      HandshakeFileType handshakeFileType = new HandshakeFileType();
      handshakeFileType.setType(type);
      return handshakeFileType;
   }

   public static LogDataType logDataType(byte type)
   {
      LogDataType logDataType = new LogDataType();
      logDataType.setType(type);
      return logDataType;
   }

   public static YoType yoType(byte type)
   {
      YoType yoType = new YoType();
      yoType.setType(type);
      return yoType;
   }
}
