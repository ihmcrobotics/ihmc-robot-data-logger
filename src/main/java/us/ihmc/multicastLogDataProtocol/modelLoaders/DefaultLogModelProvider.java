package us.ihmc.multicastLogDataProtocol.modelLoaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import us.ihmc.log.LogTools;
import us.ihmc.tools.ResourceLoaderTools;

public class DefaultLogModelProvider<T> implements LogModelProvider
{
   private final Class<T> modelLoader;
   private final String modelName;
   private final byte[] model;
   private final Predicate<String> filter;
   private final String[] topLevelResourceDirectories;

   public DefaultLogModelProvider(Class<T> modelLoader,
                                  String modelName,
                                  InputStream modelFileAsStream,
                                  Predicate<String> filter,
                                  String[] topLevelResourceDirectories)
   {
      this.modelLoader = modelLoader;
      this.modelName = modelName;

      try
      {
         this.model = IOUtils.toByteArray(modelFileAsStream);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }

      this.filter = filter;
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
         ResourceLoaderTools.createZipBundle(os, filter, topLevelResourceDirectories);
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
      return modelName;
   }
}
