package us.ihmc.robotDataLogger.logger;

import logger_msgs.msg.dds.LogProperties;
import us.ihmc.idl.serializers.extra.ROS2PropertiesSerializer;

import java.io.File;
import java.io.IOException;

public class LogPropertiesWriter extends LogProperties
{
   private final static String version = "4.0";
   private final File file;

   public LogPropertiesWriter(File file)
   {
      super();
      this.file = file;
      if (file.exists())
      {
         throw new RuntimeException("Properties file " + file.getAbsolutePath() + " already exists");
      }
      setVersion(version);
      // Backwards comparability options
      getVideo().setHasTimebase(true);
   }

   public void store() throws IOException
   {
      ROS2PropertiesSerializer<LogProperties> serializer = new ROS2PropertiesSerializer<>(LogProperties.class);
      serializer.serialize(file, this);
   }

}
