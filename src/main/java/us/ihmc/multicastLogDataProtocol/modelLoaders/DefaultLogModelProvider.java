package us.ihmc.multicastLogDataProtocol.modelLoaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import us.ihmc.log.LogTools;
import us.ihmc.tools.ResourceLoaderTools;

public class DefaultLogModelProvider<T> implements LogModelProvider
{
   private final String sdfModelName;
   private final byte[] model;
   private final String[] topLevelResourceDirectories;
   private final Class<T> modelLoader;

   public DefaultLogModelProvider(Class<T> modelLoader, String modelName, InputStream modelFileAsStream, String[] topLevelResourceDirectories)
   {
      this.modelLoader = modelLoader;
      this.sdfModelName = modelName;

      try
      {
         this.model = IOUtils.toByteArray(modelFileAsStream);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      this.topLevelResourceDirectories = new String[topLevelResourceDirectories.length];
      System.arraycopy(topLevelResourceDirectories, 0, this.topLevelResourceDirectories, 0, topLevelResourceDirectories.length);
   }

   @Override
   public Class<T> getLoader()
   {
      return modelLoader;
   }

   @Override
   public String[] getTopLevelResourceDirectories()
   {
      return topLevelResourceDirectories;
   }

   @Override
   public byte[] getResourceZip()
   {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      try
      {
         // Online directories matched in this regular expression will be logged
         Pattern zipInclude = Pattern.compile("models\\\\nadia_V17_description\\\\.*");
         ResourceLoaderTools.createZipBundle(os, zipInclude, topLevelResourceDirectories);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      return os.toByteArray();
   }

   @Override
   public byte[] getModel()
   {
      return model;
   }

   @Override
   public String getModelName()
   {
      return sdfModelName;
   }
}
