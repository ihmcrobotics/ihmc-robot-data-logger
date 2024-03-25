package us.ihmc.multicastLogDataProtocol.modelLoaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import org.apache.commons.io.IOUtils;
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
                                  Collection<InputStream> modelsAsStreams,
                                  Predicate<String> filter,
                                  String[] topLevelResourceDirectories)
   {
      this.modelLoader = modelLoader;
      this.modelName = modelName;

      try
      {
         this.model = packModels(modelsAsStreams);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }

      this.filter = filter;
      this.topLevelResourceDirectories = new String[topLevelResourceDirectories.length];
      System.arraycopy(topLevelResourceDirectories, 0, this.topLevelResourceDirectories, 0, topLevelResourceDirectories.length);
   }

   /**
    * Returns the byte array of the combined InputStreams to be sent over the network
    * @param models the collection of InputStream's that will be combined and sent over the network
    * @return the combined byte array of InputStreams with metadata that allows for getting the original InputStreams back
    */
   private static byte[] packModels(Collection<InputStream> models) throws IOException
   {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      for (InputStream model : models)
      {
         // We need to get the length of data to write to set the metadata correct so that on the client side it knows how long each model is.
         // So we go through each stream to get the length to add that to the metadata
         ByteArrayOutputStream temp = new ByteArrayOutputStream();
         byte[] buffer = new byte[4096];
         int bytesRead;
         while ((bytesRead = model.read(buffer)) != -1)
         {
            temp.write(buffer, 0, bytesRead);
         }
         long length = temp.size();
         byte[] data = temp.toByteArray();

         // Write the length as a 4-byte integer (32 bits)
         outputStream.write((int) (length >> 24));
         outputStream.write((int) (length >> 16));
         outputStream.write((int) (length >> 8));
         outputStream.write((int) length);
         // Write the data after the meta data
         outputStream.write(data);
      }

      return outputStream.toByteArray();
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
