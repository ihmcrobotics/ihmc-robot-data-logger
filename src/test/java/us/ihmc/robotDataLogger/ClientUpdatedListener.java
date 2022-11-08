package us.ihmc.robotDataLogger;

import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;
import us.ihmc.yoVariables.registry.YoRegistry;

public class ClientUpdatedListener implements YoVariablesUpdatedListener
{
   private YoVariableClientInterface yoVariableClientInterface;
   private YoRegistry parentRegistry;
   private YoRegistry clientRootRegistry;
   private boolean handshakeComplete;

   @Override
   public boolean updateYoVariables()
   {
      return false;
   }

   @Override
   public boolean changesVariables()
   {
      return false;
   }

   @Override
   public void setShowOverheadView(boolean showOverheadView)
   {

   }

   @Override
   public void start(YoVariableClientInterface yoVariableClientInterface,
                     LogHandshake handshake,
                     YoVariableHandshakeParser handshakeParser,
                     DebugRegistry debugRegistry)
   {

   }

   @Override
   public void disconnected()
   {

   }

   @Override
   public void receivedTimestampAndData(long timestamp)
   {

   }

   @Override
   public void connected()
   {

   }

   @Override
   public void receivedCommand(DataServerCommand command, int argument)
   {

   }

   @Override
   public void receivedTimestampOnly(long timestamp)
   {

   }
}
