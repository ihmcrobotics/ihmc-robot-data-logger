package us.ihmc.tools.compression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.ihmc.tools.ResourceLoaderTools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

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

   @Test
   public void testCreatingZipBundleWithOutModels()
   {
      createZipBundle(os, null, resourceDirectories);
   }

   @Test
   public void testCreatingZipWithModels()
   {
      // This should include the directory your_mom from the zip bundle
      Pattern include = Pattern.compile("models\\\\your_mom\\\\.*");
      createZipBundle(os, include, resourceDirectories);
   }

   public void createZipBundle(ByteArrayOutputStream os, Pattern include, String[] resourceDirectories)
   {
      try
      {
         ResourceLoaderTools.createZipBundle(os, include, resourceDirectories);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }
}
