package us.ihmc.robotDataLogger;

import logger_msgs.Announcement;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.idl.serializers.extra.ROS2JSONSerializer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IDLSequenceSerializationTest
{
   @Test
   public void testIDLStringSequenceSerializesAsArray() throws Exception
   {
      Announcement announcement = new Announcement();
      announcement.setName("TestServer");
      announcement.setHostName("test-host");

      IDLStringSequence resourceDirs = announcement.getModelFileDescription().getResourceDirectories();
      resourceDirs.add("path1");
      resourceDirs.add("path2");
      resourceDirs.add("path3");

      ROS2JSONSerializer<Announcement> serializer = new ROS2JSONSerializer<>(Announcement.class);
      String json = serializer.serializeToString(announcement);

      assertFalse(json.contains("IDLStringSequence@"), "IDLStringSequence was serialized as toString() instead of array");
      assertTrue(json.contains("\"path1\"") && json.contains("\"path2\"") && json.contains("\"path3\""));
   }
}
