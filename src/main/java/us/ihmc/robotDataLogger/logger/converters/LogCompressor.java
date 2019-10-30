package us.ihmc.robotDataLogger.logger.converters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import us.ihmc.idl.serializers.extra.PropertiesSerializer;
import us.ihmc.robotDataLogger.LogProperties;
import us.ihmc.robotDataLogger.LogPropertiesPubSubType;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.logger.LogPropertiesReader;
import us.ihmc.robotDataLogger.logger.YoVariableLoggerListener;
import us.ihmc.tools.compression.SnappyUtils;

public class LogCompressor extends SimpleFileVisitor<Path>
{
   private final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:robotData.log");

   public static void main(String[] args) throws IOException
   {
      Path root;
      if (args.length < 1)
      {
         System.out.println("Enter directory to convert");
         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         String path = br.readLine();
         br.close();
         root = Paths.get(path);
      }
      else
      {
         root = Paths.get(args[0]);
      }

      if (!Files.exists(root) || !Files.isDirectory(root))
      {
         throw new RuntimeException(root + " is not a directory");
      }
      Files.walkFileTree(root, new LogCompressor());
   }

   @Override
   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
   {
      if (matcher.matches(file.getFileName()))
      {
         try
         {
            File directory = file.getParent().toFile();
            File log = new File(directory, YoVariableLoggerListener.propertyFile);
            LogProperties properties = new LogPropertiesReader(log);
            compress(directory, properties);
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
      }
      return FileVisitResult.CONTINUE;
   }

   @Override
   public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException
   {

      System.err.println(exc.getMessage());
      return FileVisitResult.CONTINUE;
   }

   @SuppressWarnings("resource")
   public static void compress(File directory, LogProperties properties) throws IOException
   {
      if (!properties.getVariables().getCompressed())
      {
         System.out.println("Compressing " + directory);

         YoVariableHandshakeParser handshake = ConverterUtil.getHandshake(properties.getVariables().getHandshakeFileType(),
                                                                          new File(directory, properties.getVariables().getHandshakeAsString()));
         int bufferSize = handshake.getBufferSize();

         File logdata = new File(directory, properties.getVariables().getDataAsString());
         if (!logdata.exists())
         {
            throw new RuntimeException("Cannot find " + properties.getVariables().getDataAsString());
         }

         properties.getVariables().setCompressed(true);
         properties.getVariables().setData("robotData.bsz");
         properties.getVariables().setIndex("robotData.dat");
         properties.getVariables().setTimestamped(true);
         File compressedData = new File(directory, properties.getVariables().getDataAsString());
         File indexData = new File(directory, properties.getVariables().getIndexAsString());

         ByteBuffer indexBuffer = ByteBuffer.allocate(16);
         ByteBuffer compressed = ByteBuffer.allocate(SnappyUtils.maxCompressedLength(bufferSize));
         ByteBuffer uncompressed = ByteBuffer.allocate(bufferSize);
         FileChannel logChannel = new FileInputStream(logdata).getChannel();

         FileChannel compressedChannel = new FileOutputStream(compressedData).getChannel();
         FileChannel indexChannel = new FileOutputStream(indexData).getChannel();

         int count = 0;

         int elements = (int) (logChannel.size() / bufferSize);
         while (logChannel.read(uncompressed) != -1)
         {
            if (count % (elements / 10) == 0)
            {
               System.out.print(count / (elements / 100) + "%");
            }
            else if (count % (elements / 100) == 0)
            {
               System.out.print(".");
            }

            if (uncompressed.position() != uncompressed.limit())
            {
               throw new RuntimeException("Did not read full length segment");
            }

            uncompressed.flip();
            SnappyUtils.compress(uncompressed, compressed);
            compressed.flip();

            indexBuffer.putLong(uncompressed.getLong(0));
            indexBuffer.putLong(compressedChannel.position());
            indexBuffer.flip();
            indexChannel.write(indexBuffer);

            compressedChannel.write(compressed);

            indexBuffer.clear();
            uncompressed.clear();
            compressed.clear();

            count++;
         }
         System.out.println();

         indexChannel.close();
         compressedChannel.close();
         logChannel.close();

         File log = new File(directory, "robotData.log");
         PropertiesSerializer<LogProperties> writer = new PropertiesSerializer<>(new LogPropertiesPubSubType());
         writer.serialize(log, properties);

         logdata.delete();
         System.out.println("Compressed " + directory);
      }
      else
      {
         System.err.println("Log file is already compressed: " + directory);
      }
   }
}
