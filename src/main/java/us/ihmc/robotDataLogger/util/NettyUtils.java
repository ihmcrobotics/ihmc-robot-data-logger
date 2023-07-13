package us.ihmc.robotDataLogger.util;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import us.ihmc.log.LogTools;

/**
 * Utility class for getting implementations of Netty transport classes depending on platform capability
 */
public final class NettyUtils
{
   private static final boolean useNativeTransport = Epoll.isAvailable();

   static
   {
      if (useNativeTransport)
      {
         LogTools.info("Netty will use the native transport implementation (Epoll)");
      }
   }

   public static EventLoopGroup createEventGroundLoop(int threads)
   {
      return useNativeTransport ? new EpollEventLoopGroup(threads) : new NioEventLoopGroup(threads);
   }

   public static EventLoopGroup createEventGroundLoop()
   {
      return createEventGroundLoop(0);
   }

   public static Class<? extends SocketChannel> getSocketChannelClass()
   {
      return useNativeTransport ? EpollSocketChannel.class : NioSocketChannel.class;
   }

   public static Class<? extends ServerSocketChannel> getServerSocketChannelClass()
   {
      return useNativeTransport ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
   }
}
