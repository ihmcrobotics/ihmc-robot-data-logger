package us.ihmc.robotDataLogger.util;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class SocketUtils
{
   /**
    * Check if a UDP port is in use on a specific interface.
    *
    * @param networkInterface
    * @param port
    * @return true if in use, false if not
    */
   public static boolean isUDPPortInUse(NetworkInterface networkInterface, int port)
   {
      try
      {
         Enumeration<InetAddress> address = networkInterface.getInetAddresses();

         while(address.hasMoreElements())
         {
            InetAddress inetAddress = address.nextElement();
            if (!inetAddress.isLoopbackAddress())
            {
               DatagramSocket socket = new DatagramSocket(port, inetAddress);
               socket.close();
               socket.disconnect();
               return false;
            }
         }
      }
      catch (BindException e)
      {
         return true; // Port is already in use
      }
      catch (Exception e)
      {
         e.printStackTrace();
         return true; // Other errors
      }

      return true; // Other errors
   }
}
