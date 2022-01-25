package us.ihmc.publisher.logger.ui;

import java.util.Collections;
import java.util.List;

import gnu.trove.list.array.TByteArrayList;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import us.ihmc.commons.MathTools;
import us.ihmc.robotDataLogger.Host;

public class HostBean
{
   public static class CameraHolder
   {
      private TByteArrayList cameras = new TByteArrayList();

      public CameraHolder(List<CameraBean> cameras)
      {
         for (CameraBean camera : cameras)
         {
            this.cameras.add((byte) MathTools.clamp(camera.getCamera_id(), 0, 127));
         }
      }
      
      private CameraHolder()
      {
         
      }

      @Override
      public String toString()
      {
         return cameras.toString();
      }
   }

   public final SimpleStringProperty hostname = new SimpleStringProperty("localhost");
   public final SimpleIntegerProperty port = new SimpleIntegerProperty(8008);

   public final SimpleObjectProperty<CameraHolder> cameras = new SimpleObjectProperty<CameraHolder>(new CameraHolder(Collections.emptyList()));
   
   public HostBean()
   {
      
   }

   public HostBean(Host host)
   {
      hostname.set(host.getHostnameAsString());
      port.set(host.getPort());
      
      CameraHolder holder = new CameraHolder();
      for (int i = 0; i < host.getCameras().size(); i++)
      {
         holder.cameras.add(host.getCameras().get(i));
      }
      
      setCameras(holder);
   }

   public String getHostname()
   {
      return hostname.get();
   }

   public int getPort()
   {
      return port.get();
   }

   public CameraHolder getCameras()
   {
      return cameras.get();
   }

   public void setCameras(CameraHolder cameras)
   {
      this.cameras.set(cameras);
   }

   public void pack(Host host)
   {
      host.setHostname(getHostname());
      host.setPort(getPort());

      for (int i = 0; i < getCameras().cameras.size(); i++)
      {
         byte camera = getCameras().cameras.get(i);
         host.getCameras().add(camera);
      }

   }


}
