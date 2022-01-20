package us.ihmc.robotDataLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import us.ihmc.idl.serializers.extra.YAMLSerializer;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerDescription;

public class CameraSettingsLoader
{
   public static final String location = System.getProperty("user.home") + File.separator + ".ihmc" + File.separator + "CameraSettings.yaml";

   public static CameraSettings load()
   {
      File in = new File(location);

      if(in.exists())
      {
         try
         {
            String data = new String(Files.readAllBytes(in.toPath()), Charset.defaultCharset());
            return load(data);
         }
         catch (IOException e)
         {
            LogTools.warn("Cannot load camera settings: " + e.getMessage());
         }
      }
      else
      {
         LogTools.warn("Cannot find " + location + ". No cameras available.");

      }
      
      return new CameraSettings();
   }

   public static CameraSettings load(String data)
   {
      YAMLSerializer<CameraSettings> ser = new YAMLSerializer<>(new CameraSettingsPubSubType());
      ser.setAddTypeAsRootNode(false);

      try
      {
         return ser.deserialize(data);
      }
      catch (IOException e)
      {
         LogTools.warn("Cannot load camera settings: " + e.getMessage());

         return new CameraSettings();

      }

   }
   
   public static String toString(List<HTTPDataServerDescription> list) throws IOException
   {
      YAMLSerializer<StaticHostList> ser = new YAMLSerializer<>(new StaticHostListPubSubType());
      ser.setAddTypeAsRootNode(false);


      StaticHostList staticHostList = new StaticHostList();
      for (HTTPDataServerDescription description : list)
      {
         Host host = staticHostList.getHosts().add();
         host.setHostname(description.getHost());
         host.setPort(description.getPort());
         host.getCameras().addAll(description.getCameraList());
      }

      return ser.serializeToString(staticHostList);
   }

   public static void save(List<HTTPDataServerDescription> list) throws IOException
   {
      File in = new File(location);
      if (!in.getParentFile().exists())
      {
         in.getParentFile().mkdirs();
      }

      Files.write(in.toPath(), toString(list).getBytes());

   }

}
