package us.ihmc.robotDataLogger.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import logger_msgs.Handshake;
import logger_msgs.MessageTypes;
import org.junit.jupiter.api.Test;

import us.ihmc.idl.serializers.extra.ROS2YAMLSerializer;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class IDLYoVariableHandshakeParserYamlTest
{
   @Test
   void roundTripsNewYamlRootKey() throws IOException
   {
      Handshake handshake = buildSampleHandshake();
      ROS2YAMLSerializer<Handshake> serializer = new ROS2YAMLSerializer<>(Handshake.class);

      byte[] yamlBytes = serializer.serializeToBytes(handshake);

      IDLYoVariableHandshakeParser parser = (IDLYoVariableHandshakeParser) YoVariableHandshakeParser.create(MessageTypes.IDL_YAML);
      parser.parseFrom(yamlBytes);

      assertTrue(parser.getYoVariablesList().stream().anyMatch(v -> "var0".equals(v.getName())));
   }

   @Test
   void readsLegacyYamlRootKey() throws IOException
   {
      String legacyYaml = """
            us::ihmc::robotDataLogger::Handshake:
              dt: 0.001
              registries:
                - parent: 0
                  name: root
                - parent: 0
                  name: main
              variables:
                - name: var0
                  description: test
                  type: DoubleYoVariable
                  registry: 1
                  enumType: 0
                  allowNullValues: false
                  isParameter: false
                  min: 0.0
                  max: 1.0
                  loadStatus: NoParameter
              joints: []
              graphicObjects: []
              artifacts: []
              scs2YoGraphicDefinitions: []
              enumTypes: []
              referenceFrameInformation:
                frameNames: []
                frameIndices: []
              summary:
                createSummary: false
            """;

      IDLYoVariableHandshakeParser parser = (IDLYoVariableHandshakeParser) YoVariableHandshakeParser.create(MessageTypes.IDL_YAML);
      parser.parseFrom(legacyYaml.getBytes(StandardCharsets.UTF_8));

      assertEquals(0.001, parser.getDt());
      assertEquals(1, parser.getYoVariablesList().size());
      assertEquals("var0", parser.getYoVariablesList().get(0).getName());
   }

   @Test
   void newSerializerProducesReadableRootKey() throws IOException
   {
      Handshake handshake = buildSampleHandshake();
      ROS2YAMLSerializer<Handshake> serializer = new ROS2YAMLSerializer<>(Handshake.class);
      String yaml = serializer.serializeToString(handshake);

      assertNotNull(serializer.deserialize(yaml));
   }

   private static Handshake buildSampleHandshake()
   {
      YoRegistry root = new YoRegistry("root");
      YoRegistry main = new YoRegistry("main");
      root.addChild(main);
      new YoDouble("var0", main);

      YoVariableHandShakeBuilder builder = new YoVariableHandShakeBuilder(root.getName(), 0.001);
      builder.addRegistryBuffer(new RegistrySendBufferBuilder(main));
      return builder.getHandShake();
   }
}
