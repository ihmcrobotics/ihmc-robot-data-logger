package us.ihmc.robotDataLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.ihmc.robotDataLogger.websocket.client.discovery.HTTPDataServerDescription;

public class StaticHostListLoaderTest
{
   @Test
   void readsHostCameraIdsFromLegacyYaml()
   {
      String legacyYaml = """
            disableAutoDiscovery: false
            hosts:
              - hostname: 192.168.1.10
                port: 8008
                cameras: [1, 2]
            """;

      List<HTTPDataServerDescription> hosts = StaticHostListLoader.load(legacyYaml);

      assertEquals(1, hosts.size());
      HTTPDataServerDescription host = hosts.get(0);
      assertEquals("192.168.1.10", host.getHost());
      assertEquals(8008, host.getPort());
      assertNotNull(host.getCameraList());
      assertEquals(2, host.getCameraList().size());
      assertEquals((byte) 1, host.getCameraList().getBuffer().get(0));
      assertEquals((byte) 2, host.getCameraList().getBuffer().get(1));
   }

   @Test
   void readsLegacyRootKeyYaml()
   {
      String legacyYaml = """
            us::ihmc::robotDataLogger::StaticHostList:
              disableAutoDiscovery: true
              hosts:
                - hostname: atlas-pc
                  port: 8010
                  cameras: [0]
            """;

      List<HTTPDataServerDescription> hosts = StaticHostListLoader.load(legacyYaml);

      assertEquals(1, hosts.size());
      assertEquals("atlas-pc", hosts.get(0).getHost());
      assertEquals(8010, hosts.get(0).getPort());
      assertEquals(1, hosts.get(0).getCameraList().size());
      assertEquals((byte) 0, hosts.get(0).getCameraList().getBuffer().get(0));
   }
}
