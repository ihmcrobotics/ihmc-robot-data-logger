package us.ihmc.robotDataLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.ihmc.idl.serializers.extra.YAMLSerializer;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerDescription;

public class StaticHostListLoader
{
   public static final String location = System.getProperty("user.home") + File.separator + ".ihmc" + File.separator + "ControllerHosts.yaml";

   public static List<HTTPDataServerDescription> load()
   {
      File in = new File(location);

      if (in.exists())
      {
         try
         {
            String data = new String(Files.readAllBytes(in.toPath()), Charset.defaultCharset());
            return load(data);
         }
         catch (IOException e)
         {
            LogTools.warn("Cannot load hosts list: " + e.getMessage());
         }
      }
      else
      {
         LogTools.warn("Cannot find " + location + ". Starting with empty list of hosts.");

      }

      return Collections.emptyList();

   }
   
   
   public static StaticHostList loadHostList(String data) throws IOException
   {
      YAMLSerializer<StaticHostList> ser = new YAMLSerializer<>(new StaticHostListPubSubType());
      ser.setAddTypeAsRootNode(false);

      return ser.deserialize(data);

   }

   public static List<HTTPDataServerDescription> load(String data)
   {

      try
      {
         List<HTTPDataServerDescription> list = new ArrayList<>();
         StaticHostList hostList = loadHostList(data);
         for (Host host : hostList.getHosts())
         {
            HTTPDataServerDescription description = new HTTPDataServerDescription(host.getHostnameAsString(), host.getPort(), host.getCameras(), true);
            list.add(description);
         }

         return list;
      }
      catch (IOException e)
      {
         LogTools.warn("Cannot load hosts list: " + e.getMessage());
      }

      return Collections.emptyList();
   }

   public static String toString(List<HTTPDataServerDescription> list) throws IOException
   {

      StaticHostList staticHostList = new StaticHostList();
      for (HTTPDataServerDescription description : list)
      {
         Host host = staticHostList.getHosts().add();
         host.setHostname(description.getHost());
         host.setPort(description.getPort());
         
         if(description.getCameraList() != null)
         {
            host.getCameras().addAll(description.getCameraList());
         }
      }

      return toString(staticHostList);
   }


   public static String toString(StaticHostList staticHostList) throws IOException
   {
      YAMLSerializer<StaticHostList> ser = new YAMLSerializer<>(new StaticHostListPubSubType());
      ser.setAddTypeAsRootNode(false);
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
