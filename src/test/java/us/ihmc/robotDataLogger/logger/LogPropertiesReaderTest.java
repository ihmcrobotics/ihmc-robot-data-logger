package us.ihmc.robotDataLogger.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import logger_msgs.HandshakeFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LogPropertiesReaderTest
{
   @Test
   void readsNestedVariablesFromProperties(@TempDir File tempDir) throws IOException
   {
      File propertiesFile = new File(tempDir, YoVariableLoggerListener.propertyFile);
      Files.writeString(propertiesFile.toPath(), """
            version=4.0
            name=testLog
            variables.handshakeFileType=IDL_YAML
            variables.handshake=handshake.yaml
            variables.data=robotData.bsz
            variables.summary=summary.csv
            variables.index=robotData.dat
            variables.timestamped=false
            variables.compressed=true
            """);

      LogPropertiesReader properties = new LogPropertiesReader(propertiesFile);

      assertEquals("4.0", properties.getVersionAsString());
      assertEquals("testLog", properties.getNameAsString());
      assertEquals("handshake.yaml", properties.getVariables().getHandshakeAsString());
      assertEquals("robotData.bsz", properties.getVariables().getDataAsString());
      assertEquals(HandshakeFileType.IDL_YAML, properties.getVariables().getHandshakeFileType());
   }

   @Test
   void readsNumericHandshakeFileType(@TempDir File tempDir) throws IOException
   {
      File propertiesFile = new File(tempDir, YoVariableLoggerListener.propertyFile);
      Files.writeString(propertiesFile.toPath(), """
            version=4.0
            name=testLog
            variables.handshakeFileType=1
            variables.handshake=handshake.yaml
            variables.data=robotData.bsz
            """);

      LogPropertiesReader properties = new LogPropertiesReader(propertiesFile);

      assertEquals(HandshakeFileType.IDL_YAML, properties.getVariables().getHandshakeFileType());
   }

   @Test
   void readsCommaSeparatedResourceDirectories(@TempDir File tempDir) throws IOException
   {
      File propertiesFile = new File(tempDir, YoVariableLoggerListener.propertyFile);
      Files.writeString(propertiesFile.toPath(), """
            version=4.0
            name=testLog
            model.name=Atlas
            model.path=model.sdf
            model.resourceBundle=resources.zip
            model.resourceDirectoriesList=us.ihmc.models,us.ihmc.resources
            variables.handshake=handshake.yaml
            variables.data=robotData.bsz
            """);

      LogPropertiesReader properties = new LogPropertiesReader(propertiesFile);

      assertEquals(2, properties.getModel().getResourceDirectoriesList().size());
      assertEquals("us.ihmc.models", properties.getModel().getResourceDirectoriesList().getAsString(0));
      assertEquals("us.ihmc.resources", properties.getModel().getResourceDirectoriesList().getAsString(1));
   }

   @Test
   void writesLegacyCompatibleProperties(@TempDir File tempDir) throws IOException
   {
      File propertiesFile = new File(tempDir, "robotData.log");
      LogPropertiesWriter writer = new LogPropertiesWriter(propertiesFile);
      writer.setName("testLog");
      writer.getVariables().setHandshakeFileType(HandshakeFileType.IDL_YAML);
      writer.getVariables().setHandshake("handshake.yaml");
      writer.getVariables().setData("robotData.bsz");
      writer.getVariables().setIndex("robotData.dat");
      writer.store();

      String contents = Files.readString(propertiesFile.toPath());
      assertTrue(contents.contains("variables.handshakeFileType=IDL_YAML"));
      assertTrue(!contents.contains("AsString"));
      assertTrue(!contents.contains("variables.handshakeFileType=1"));

      LogPropertiesReader properties = new LogPropertiesReader(propertiesFile);
      assertEquals(HandshakeFileType.IDL_YAML, properties.getVariables().getHandshakeFileType());
      assertEquals("handshake.yaml", properties.getVariables().getHandshakeAsString());
   }
}
