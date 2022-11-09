package us.ihmc.robotDataLogger;

import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

import java.util.List;

public class ClientUpdatedListener implements YoVariablesUpdatedListener
{
   private YoRegistry parentRegistry;

   public ClientUpdatedListener(YoRegistry parentRegistry)
   {
      this.parentRegistry = parentRegistry;
   }

   // Returns a list of the variables that were set when creating the server, this is how I access the server variables from the client
   public List<YoVariable> getConnectedClientVariables()
   {
      return parentRegistry.getChildren().get(0).getChildren().get(0).getChildren().get(0).getVariables();
   }


   // NEed to update these variables, that is why the client listener isn't getting updates
   @Override
   public boolean updateYoVariables()
   {
      return true;
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

      YoRegistry clientRootRegistry = handshakeParser.getRootRegistry();
      YoRegistry serverRegistry = new YoRegistry(yoVariableClientInterface.getServerName() + "Container");
      serverRegistry.addChild(clientRootRegistry);
      parentRegistry.addChild(serverRegistry);
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
