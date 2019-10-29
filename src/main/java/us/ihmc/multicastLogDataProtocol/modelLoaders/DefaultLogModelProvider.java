package us.ihmc.multicastLogDataProtocol.modelLoaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import us.ihmc.tools.ClassLoaderTools;

public class DefaultLogModelProvider<T> implements LogModelProvider
{
   private final String sdfModelName;
   private final byte[] model;
   private final String[] resourceDirectories;
   private final Class<T> modelLoader;

   public DefaultLogModelProvider(Class<T> modelLoader, String modelName, InputStream modelFileAsStream, String[] resourceDirectories)
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

      this.resourceDirectories = new String[resourceDirectories.length];
      System.arraycopy(resourceDirectories, 0, this.resourceDirectories, 0, resourceDirectories.length);
   }

   @Override
   public Class<T> getLoader()
   {
      return modelLoader;
   }

   @Override
   public String[] getResourceDirectories()
   {
      return resourceDirectories;
   }

   @Override
   public byte[] getResourceZip()
   {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      try
      {
         Pattern zipExclude = null; //Pattern.compile(".*\\.(?i)(zip)$");
         ClassLoaderTools.createZipBundle(os, zipExclude, resourceDirectories);
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
