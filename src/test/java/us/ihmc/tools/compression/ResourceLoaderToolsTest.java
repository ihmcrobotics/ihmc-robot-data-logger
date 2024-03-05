package us.ihmc.tools.compression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.ihmc.tools.ResourceLoaderTools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Predicate;

public class ResourceLoaderToolsTest
{
   private final String[] topLevelResourceDirectories = {"models/", "models/gazebo/", "models/nadia_description/", "models/nadia_description/sdf/"};
   private String[] resourceDirectories;
   private final ByteArrayOutputStream os = new ByteArrayOutputStream();

   @BeforeEach
   public void startServerAndClientAndSetupResourceDirectories()
   {
      resourceDirectories = new String[topLevelResourceDirectories.length];
      System.arraycopy(topLevelResourceDirectories, 0, resourceDirectories, 0, resourceDirectories.length);
   }

   // These tests don't really serve any purpose other than to see what code is being run when using a test coverage tool
   @Test
   public void testCreatingZipBundleWithAllModels() throws IOException
   {
      ResourceLoaderTools.createZipBundle(os, null, resourceDirectories);
   }

   // These tests don't really serve any purpose other than to see what code is being run when using a test coverage tool
   @Test
   public void testCreatingZipBundleWithFilteredModels() throws IOException
   {
      // This should include the directory your_mom from the zip bundle
      String[] resourceModelsToBeLogged = {"models\\nadia_V17"};
      Predicate<String> filter = resourcePath ->
      {
         for (String model : resourceModelsToBeLogged)
         {
            if(resourcePath.startsWith(model))
               return true;
         }

         return false;
      };
      ResourceLoaderTools.createZipBundle(os, filter, resourceDirectories);
   }
}
