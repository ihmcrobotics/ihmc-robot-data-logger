package us.ihmc.robotDataLogger.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.ihmc.robotDataLogger.util.SocketUtils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class SocketUtilsTest
{
   private NetworkInterface networkInterface;
   private InetAddress validAddress;

   // Choose an unused port
   int port = 54321;

   @BeforeEach
   public void setUp() throws Exception
   {
      // Get a network interface that has at least one address and is not a loop back

      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
      {
         if (ni.isUp() && !ni.isLoopback() && ni.getInetAddresses().hasMoreElements())
         {
            networkInterface = ni;
            validAddress = ni.getInetAddresses().nextElement();
            break;
         }
      }
   }

   @Test
   public void testUDPPortIsAvailable()
   {
      boolean inUse = SocketUtils.isUDPPortInUse(networkInterface, port);
      assertFalse(inUse, "Port should be available, but was reported as in use");
   }

   @Test
   public void testUDPPortIsInUse() throws Exception
   {
      // Open a socket to simulate that the port is in use
      try (DatagramSocket socket = new DatagramSocket(port, validAddress))
      {
         boolean inUse = SocketUtils.isUDPPortInUse(networkInterface, port);
         assertTrue(inUse, "Port should be in use, but was reported as available");
      }
   }
}
