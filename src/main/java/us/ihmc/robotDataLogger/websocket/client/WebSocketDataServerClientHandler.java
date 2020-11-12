package us.ihmc.robotDataLogger.websocket.client;

import java.net.SocketException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import us.ihmc.log.LogTools;
import us.ihmc.pubsub.common.SerializedPayload;
import us.ihmc.robotDataLogger.YoVariableClientImplementation;
import us.ihmc.robotDataLogger.dataBuffers.CustomLogDataSubscriberType;
import us.ihmc.robotDataLogger.dataBuffers.RegistryConsumer;
import us.ihmc.robotDataLogger.dataBuffers.RegistryReceiveBuffer;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;

public class WebSocketDataServerClientHandler extends SimpleChannelInboundHandler<Object>
{
   private final WebSocketClientHandshaker handshaker;
   private final RegistryConsumer consumer;
   private final YoVariableClientImplementation yoVariableClient;

   private final CustomLogDataSubscriberType type;
   private final SerializedPayload payload;

   private final int timestampPort;

   private ChannelPromise handshakeFuture;

   private boolean sendConfiguration = false;

   private volatile boolean waitingForPong = false;

   public WebSocketDataServerClientHandler(WebSocketClientHandshaker handshaker,
                                           YoVariableClientImplementation yoVariableClient,
                                           int timestampPort,
                                           RegistryConsumer consumer,
                                           CustomLogDataSubscriberType type)
   {
      this.handshaker = handshaker;
      this.yoVariableClient = yoVariableClient;
      this.consumer = consumer;
      this.type = type;
      this.timestampPort = timestampPort;

      payload = new SerializedPayload(type.getTypeSize());
   }

   public ChannelFuture handshakeFuture()
   {
      return handshakeFuture;
   }

   @Override
   public void handlerAdded(ChannelHandlerContext context)
   {
      handshakeFuture = context.newPromise();
   }

   @Override
   public void channelActive(ChannelHandlerContext context)
   {
      handshaker.handshake(context.channel());
   }

   @Override
   public void channelRead0(ChannelHandlerContext context, Object message) throws Exception
   {
      Channel channel = context.channel();
      if (!handshaker.isHandshakeComplete())
      {
         handshaker.finishHandshake(channel, (FullHttpResponse) message);
         yoVariableClient.connected();
         handshakeFuture.setSuccess();

         return;
      }

      if (message instanceof FullHttpResponse)
      {
         FullHttpResponse response = (FullHttpResponse) message;
         throw new IllegalStateException("Unexpected FullHttpResponse (getStatus=" + response.status() + ", content="
               + response.content().toString(CharsetUtil.UTF_8) + ')');
      }

      WebSocketFrame frame = (WebSocketFrame) message;
      if (frame instanceof TextWebSocketFrame)
      {
         DataServerCommand command = DataServerCommand.getCommand(frame.content());
         if (command != null)
         {
            int argument = command.getArgument(frame.content());
            if (argument != -1)
            {
               yoVariableClient.receivedCommand(command, argument);
            }
         }
      }
      else if (frame instanceof BinaryWebSocketFrame)
      {
         RegistryReceiveBuffer buffer = new RegistryReceiveBuffer(System.nanoTime());
         payload.getData().clear();
         payload.getData().limit(frame.content().readableBytes());
         frame.content().readBytes(payload.getData());
         payload.getData().flip();
         type.deserialize(payload, buffer);
         consumer.onNewDataMessage(buffer);

         if (!sendConfiguration)
         {
            ByteBuf sendTimestampCmd = context.alloc().buffer(DataServerCommand.MaxCommandSize());
            DataServerCommand.SEND_TIMESTAMPS.getBytes(sendTimestampCmd, timestampPort);
            TextWebSocketFrame sendTimestampFrame = new TextWebSocketFrame(sendTimestampCmd);
            channel.writeAndFlush(sendTimestampFrame);
            sendConfiguration = true;
         }
      }
      else if (frame instanceof PongWebSocketFrame)
      {
         waitingForPong = false;
      }
      else if (frame instanceof CloseWebSocketFrame)
      {
         LogTools.info("Connection closed by server");
         channel.close();
      }
   }

   @Override
   public void userEventTriggered(ChannelHandlerContext context, Object event)
   {
      if (event instanceof IdleStateEvent)
      {
         IdleState idleState = ((IdleStateEvent) event).state();
         if (idleState == IdleState.READER_IDLE)
         {
            if (waitingForPong)
            {
               LogTools.warn("Timeout receiving pong. Closing connection.");
               context.close();
            }
            else
            {
               waitingForPong = true;
               context.channel().writeAndFlush(new PingWebSocketFrame());
            }
         }
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext context, Throwable cause)
   {
      LogTools.warn("Connection closed: " + cause.getMessage());
      if (!handshakeFuture.isDone())
      {
         handshakeFuture.setFailure(cause);
      }
      context.close();
   }
}