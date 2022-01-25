package us.ihmc.publisher.logger.utils.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import us.ihmc.publisher.logger.utils.DeployConsoleInterface;

public class FXConsole implements DeployConsoleInterface
{
   private final Stage dialog = new Stage();

   private final TextArea output = new TextArea();

   private int clients = 0;

   public FXConsole(Stage parent)
   {
      dialog.initOwner(parent);
      dialog.initModality(Modality.APPLICATION_MODAL);

      dialog.setTitle("Status");

      output.setEditable(false);

      Scene scene = new Scene(output, 800, 600);
      dialog.setScene(scene);

      dialog.show();
   }

   @Override
   public void println(String line)
   {
      Platform.runLater(() ->
      {
         output.appendText(line);
         output.appendText(System.lineSeparator());

      });
   }
   
   private void closePlatform()
   {
      clients--;
      if(clients <= 0)
      {
//         dialog.close();            
      }
   }

   @Override
   public void close()
   {
      Platform.runLater(() -> closePlatform());
   }
   
   @Override
   public void closeWithMessage(String message)
   {
      Platform.runLater(() ->
      {
         Alert alert = new Alert(AlertType.INFORMATION);
         alert.setTitle("Success");
         alert.setHeaderText(message);
         alert.showAndWait();


         closePlatform();
      });

   }

   @Override
   public void closeWithError(Exception e, String errorMessage)
   {
      Platform.runLater(() ->
      {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle("Error");
         alert.setHeaderText(e.getMessage());
         if(errorMessage != null)
         {
            alert.setContentText(errorMessage);
         }
         else
         {
            alert.setContentText(e.getStackTrace()[0].toString());
         }
         alert.showAndWait();

         closePlatform();

      });
   }

   @Override
   public void open()
   {
      Platform.runLater(() -> 
      {
         clients++;
      });
   }

}
