import com.fasterxml.jackson.databind.ObjectMapper;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.idl.serializers.extra.ROS2JSONSerializer;
import logger_msgs.Announcement;

public class TestIDLSequenceSerialization {
    public static void main(String[] args) throws Exception {
        // Create an Announcement with IDLStringSequence
        Announcement announcement = new Announcement();
        announcement.setName("TestServer");
        announcement.setHostName("test-host");
        
        // Add some strings to the resource directories
        IDLStringSequence resourceDirs = announcement.getModelFileDescription().getResourceDirectories();
        resourceDirs.add("path1");
        resourceDirs.add("path2");
        resourceDirs.add("path3");
        
        // Serialize to JSON
        ROS2JSONSerializer<Announcement> serializer = new ROS2JSONSerializer<>(Announcement.class);
        String json = serializer.serializeToString(announcement);
        
        System.out.println("Serialized JSON:");
        System.out.println(json);
        
        // Check if it contains the array format, not the toString format
        if (json.contains("IDLStringSequence@")) {
            System.err.println("ERROR: IDLStringSequence was serialized as toString() instead of array!");
            System.exit(1);
        }
        
        if (json.contains("\"path1\"") && json.contains("\"path2\"") && json.contains("\"path3\"")) {
            System.out.println("\nSUCCESS: IDLStringSequence was properly serialized as an array!");
        } else {
            System.err.println("ERROR: Could not find path values in JSON!");
            System.exit(1);
        }
    }
}
