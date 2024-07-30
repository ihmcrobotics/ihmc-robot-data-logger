package us.ihmc.tools;

import us.ihmc.log.LogTools;

import java.io.*;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourceLoaderTools
{
   private static final PathMatcher classMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.class");

   /**
    * Copies all files on the classpath in the given topLevelDirectories (packages) to the output stream in zip format
    * Works only for classpaths pointing to the file system or .jar files
    *
    * @param os Output stream to write zip file to
    * @param filter Include models that match the given filter. If null grab all models
    * @param topLevelDirectories Directories (packages) to copy on the classPath
    * @throws IOException If the files cannot be written to the zip file.
    */
   public static void createZipBundle(OutputStream os, final Predicate<String> filter, String... topLevelDirectories) throws IOException
   {
      final ZipOutputStream stream = new ZipOutputStream(os);

      recursivelyGetResources(new ResourceHandler()
      {
         private final HashSet<String> names = new HashSet<>();

         @Override
         public void handleResource(String resourcePath) throws IOException
         {
            // If the filter is null, grab everything. Otherwise, only grab resources in the list
            if (filter == null || filter.test(resourcePath))
            {
               ZipEntry entry = new ZipEntry(resourcePath);

               if (names.add(entry.getName()))
               {
                  InputStream resource = ResourceLoaderTools.class.getClassLoader().getResourceAsStream(resourcePath);
                  stream.putNextEntry(entry);
                  copyStream(resource, stream);
                  stream.closeEntry();
                  resource.close();
               }
            }
         }
      }, topLevelDirectories);

      stream.close();
   }

   private static void recursivelyGetResources(final ResourceHandler resourceHandler, String... topLevelDirectories) throws IOException
   {
      HashSet<String> classPathSet = new HashSet<>();

      // Everything is awful
      Enumeration<URL> manifests = ResourceLoaderTools.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (manifests.hasMoreElements())
      {
         URL next = manifests.nextElement();
         InputStream stream = next.openStream();
         Manifest manifest = new Manifest(stream);
         String jarClassPath = manifest.getMainAttributes().getValue("Class-Path");
         if (jarClassPath != null)
         {
            classPathSet.addAll(Arrays.asList(jarClassPath.split(" ")));
         }
         stream.close();
      }

      classPathSet.addAll(Arrays.asList(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))));

      for (String nextToken : classPathSet)
      {
         Path classPath;
         try
         {
            classPath = Paths.get(nextToken);
         }
         catch (InvalidPathException e)
         {
            LogTools.error("Couldn't find: " + nextToken);
            continue;
         }

         // Some jars define non-existing classes on their Class-Path. Just silently ignore those
         if (!Files.exists(classPath))
         {
            continue;
         }

         final Path path;
         FileSystem fs;
         if (!Files.isDirectory(classPath))
         {
            try
            {
               fs = FileSystems.newFileSystem(classPath, (ClassLoader) null);
            }
            catch (Exception e)
            {
               LogTools.error("Problem creating a FileSystem for the following classpath entry, skipping: " + nextToken);
               StringWriter writer = new StringWriter();
               e.printStackTrace(new PrintWriter(writer));
               continue;
            }
            path = fs.getPath("/");
         }
         else
         {
            fs = null;
            path = classPath;
         }

         Path[] absoluteDirectories = new Path[topLevelDirectories.length];
         for (int i = 0; i < topLevelDirectories.length; i++)
         {
            absoluteDirectories[i] = path.resolve(topLevelDirectories[i]);
         }

         for (Path subdirectories : checkDirectoryExists(absoluteDirectories))
         {
            Files.walkFileTree(subdirectories, new SimpleFileVisitor<>()
            {
               @Override
               public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
               {
                  if (!classMatcher.matches(file))
                  {
                     resourceHandler.handleResource(path.relativize(file).toString());
                  }
                  return FileVisitResult.CONTINUE;
               }
            });
         }

         if (fs != null)
         {
            fs.close();
         }
      }
   }

   // This method checks if the file exists, so we don't have to do it in other places
   private static List<Path> checkDirectoryExists(Path... directories)
   {
      ArrayList<Path> topLevelDirectories = new ArrayList<>();
      for (Path directory : directories)
      {
         if (Files.notExists(directory))
            continue;

         topLevelDirectories.add(directory);
      }
      return topLevelDirectories;
   }

   private static void copyStream(InputStream is, OutputStream os) throws IOException
   {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = is.read(buffer)) > 0)
      {
         os.write(buffer, 0, n);
      }
   }

   private interface ResourceHandler
   {
      void handleResource(String resourcePath) throws IOException;
   }
}
