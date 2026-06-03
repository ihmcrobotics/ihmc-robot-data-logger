package us.ihmc.publisher.logger.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import logger_msgs.CameraConfiguration;
import logger_msgs.CameraType;

public class CameraBean
{
   public final ObjectProperty<CameraType> camera_type = new SimpleObjectProperty<>();
   public final SimpleStringProperty camera_name = new SimpleStringProperty();
   public final SimpleIntegerProperty camera_id = new SimpleIntegerProperty();
   public final SimpleIntegerProperty camera_input = new SimpleIntegerProperty();

   public CameraBean(byte id)
   {
      this.camera_id.set(id);
      camera_name.set("");
   }

   public CameraBean(CameraConfiguration config)
   {
      camera_id.set(config.getCameraId());
      camera_input.set(Integer.valueOf(config.getIdentifierAsString()));
      camera_name.set(config.getNameAsString());
   }

   public CameraType getCamera_type()
   {
      return camera_type.get();
   }

   public String getCamera_name()
   {
      return camera_name.get();
   }

   public int getCamera_id()
   {
      return camera_id.get();
   }

   public int getCamera_input()
   {
      return camera_input.get();
   }

   public void pack(CameraConfiguration camera)
   {
      camera.setType(getCamera_type().getType());
      camera.setName(getCamera_name());
      camera.setCameraId((byte) getCamera_id());
      camera.setIdentifier(String.valueOf(getCamera_input()));
   }
}
