package us.ihmc.robotDataLogger.websocket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.Host;
import us.ihmc.robotDataLogger.StaticHostList;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common functions for the DataServerLocationBroadcast client and sender
 *
 * @author Jesper Smith
 */
public abstract class DataServerLocationBroadcast
{
   private static final String PORT_MESSAGE_HEADER = "DataServerPort";

   public static class PortPOJO
   {
      public String header;
      public int port;

      public PortPOJO()
      {

      }

      public PortPOJO(int port)
      {
         header = PORT_MESSAGE_HEADER;
         this.port = port;
      }
   }

   public static final String announceGroupAddress = "239.255.24.1";
   public static final int announcePort = 55241;
   public static final int MAXIMUM_MESSAGE_SIZE = 1472;

   /**
    * Get a list of all external IP addresses.
    *
    * @return List of IP addresses
    * @throws IOException
    */
   protected static StaticHostList getMyNetworkAddresses(int dataServerPort) throws IOException
   {
      StaticHostList addresses = new StaticHostList();

      for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces()))
      {
         if (iface.isUp())
         {
            for (InetAddress addr : Collections.list(iface.getInetAddresses()))
            {
               if (addr instanceof Inet4Address)
               {
                  if (!addr.isLoopbackAddress())
                  {
                     Host host = addresses.getHosts().add();
                     host.setHostname(addr.getHostAddress());
                     host.setPort(dataServerPort);
                  }
               }
            }

         }
      }

      return addresses;
   }

   /*
   This is what prevents the logger from running twice on the same machine, the lock socket can only be bound to one port
    */
   protected static DatagramSocket acquirePortLock(int lockPort) throws IOException
   {
      DatagramSocket lockSocket = new DatagramSocket(null);

      // Needs to be false for exclusivity
      lockSocket.setReuseAddress(false);

      // Bind to all interfaces
      lockSocket.bind(new InetSocketAddress("0.0.0.0", lockPort));

      return lockSocket;
   }

   protected static List<MulticastSocket> getSocketChannelList(int bindPort, InetAddress group) throws IOException
   {
      // This list will hold one MulticastSocket per network interface
      List<MulticastSocket> sockets = new ArrayList<>();

      // Loop through all network interfaces on the machine
      for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces()))
      {
         try
         {
            // Skip interfaces that are:
            // - down (not active)
            // - loopback (127.0.0.1)
            // - don't support multicast (cannot send/receive multicast)
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast() || iface.getParent() != null)
               continue;

            // Check if the interface has at least one usable IP address
            boolean hasUsableAddress = false;
            for (InetAddress addr : Collections.list(iface.getInetAddresses()))
            {
               // Skip loopback (127.0.0.1) and link-local addresses (169.254.x.x / fe80::)
               if (addr.isLoopbackAddress() || addr.isLinkLocalAddress())
                  continue;

               hasUsableAddress = true;
               break; // found a usable address, no need to check more
            }

            // Skip interface if it has no usable IP
            if (!hasUsableAddress)
               continue;

            // Create a MulticastSocket bound to the specified port
            MulticastSocket socket = new MulticastSocket(null);

            // Allow multiple sockets to bind to the same port
            socket.setReuseAddress(true);

            socket.bind(new InetSocketAddress("0.0.0.0", bindPort));

            // Bind this socket to the current interface
            socket.setNetworkInterface(iface);

            // Join the multicast group on this interface
            // - new InetSocketAddress(group, bindPort) specifies the group + port
            // - iface specifies which network interface to use
            socket.joinGroup(new InetSocketAddress(group, bindPort), iface);

            // Add the socket to the list for use by the receiver
            sockets.add(socket);
         }
         catch (IOException e)
         {
            // Log warnings if this interface could not be used
            LogTools.error("Cannot join " + iface.getDisplayName() + ": " + e.getMessage());
            throw e;
         }
      }

      return sockets;
   }

   protected static String createMessage(int port) throws JsonProcessingException
   {
      ObjectMapper mapper = new ObjectMapper(new JsonFactory());
      PortPOJO portPOJO = new PortPOJO(port);
      return mapper.writeValueAsString(portPOJO);
   }

   protected static int parseMessage(String message, ObjectMapper mapper) throws IOException
   {
      PortPOJO portPOJO = mapper.readValue(message, PortPOJO.class);
      if (PORT_MESSAGE_HEADER.equals(portPOJO.header))
      {
         return portPOJO.port;
      }
      else
      {
         throw new JsonParseException(null, "Invalid header.");
      }

   }
}
