package us.ihmc.robotDataLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import logger_msgs.CameraConfiguration;
import logger_msgs.CameraSettings;
import logger_msgs.CameraType;
import org.junit.jupiter.api.Test;

public class CameraSettingsLoaderTest
{
   @Test
   void readsLegacyFlatYamlWithEnumTypeName() throws IOException
   {
      String legacyYaml = """
            cameras:
              - type: CAPTURE_CARD_MAGEWELL
                camera_id: 1
                name: head_camera
                identifier: "0"
            """;

      CameraSettings settings = CameraSettingsLoader.load(legacyYaml);

      assertEquals(1, settings.getCameras().size());
      CameraConfiguration camera = settings.getCameras().get(0);
      assertEquals(CameraType.CAPTURE_CARD_MAGEWELL, camera.getType());
      assertEquals((byte) 1, camera.getCameraId());
      assertEquals("head_camera", camera.getNameAsString());
      assertEquals("0", camera.getIdentifierAsString());
   }

   @Test
   void readsLegacyRootKeyYaml() throws IOException
   {
      String legacyYaml = """
            us::ihmc::robotDataLogger::CameraSettings:
              cameras:
                - type: CAPTURE_CARD_MAGEWELL
                  camera_id: 2
                  name: chest_camera
                  identifier: "1"
            """;

      CameraSettings settings = CameraSettingsLoader.load(legacyYaml);

      assertEquals(1, settings.getCameras().size());
      assertEquals((byte) 2, settings.getCameras().get(0).getCameraId());
      assertEquals("chest_camera", settings.getCameras().get(0).getNameAsString());
   }

   @Test
   void readsNumericTypeAndRoundTrips() throws IOException
   {
      CameraSettings original = new CameraSettings();
      CameraConfiguration camera = original.getCameras().add();
      camera.setType(CameraType.CAPTURE_CARD_MAGEWELL);
      camera.setCameraId((byte) 3);
      camera.setName("magewell_0");
      camera.setIdentifier("0");

      String yaml = CameraSettingsLoader.toString(original);
      CameraSettings loaded = CameraSettingsLoader.load(yaml);

      assertEquals(1, loaded.getCameras().size());
      CameraConfiguration loadedCamera = loaded.getCameras().get(0);
      assertEquals(CameraType.CAPTURE_CARD_MAGEWELL, loadedCamera.getType());
      assertEquals((byte) 3, loadedCamera.getCameraId());
      assertEquals("magewell_0", loadedCamera.getNameAsString());
      assertEquals("0", loadedCamera.getIdentifierAsString());
      assertNotNull(yaml);
   }
}
