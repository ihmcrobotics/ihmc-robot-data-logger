package us.ihmc.publisher.logger.utils.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import us.ihmc.log.LogTools;
import us.ihmc.publisher.logger.utils.DeployConsoleInterface;

public class FXConsole implements DeployConsoleInterface
{
   private final Stage dialog = new Stage();
   
   private final ObservableList<String> outputValues = FXCollections.observableArrayList();
   private final ListView<String> output = new ListView<>(outputValues);
   
   private final FileChooser fileChooser = new FileChooser();
   
   private final Button close = new Button("Close");
   private final CheckBox closeWhenFinished = new CheckBox("Close when finished");

   
   private int clients = 0;
   

   public FXConsole(Stage parent)
   {
      dialog.initOwner(parent);
      dialog.initModality(Modality.APPLICATION_MODAL);

      dialog.setTitle("Status");

      
      // Stop the user from closing this window while clients are still active
      dialog.setOnCloseRequest(e ->
      {
         if(clients > 0)
         {
            e.consume();
         }
      });
      
      
      Button exportOutput = new Button("Export output");
      exportOutput.setOnAction(e -> exportOutput());
      
      close.setOnAction(e -> tryClose());
      HBox hbox = new HBox(10, exportOutput, close);
      
      VBox vbox = new VBox(10, output, closeWhenFinished, hbox);
      
      VBox.setVgrow(output, Priority.ALWAYS);
      vbox.setPadding(new Insets(10));
      
      Scene scene = new Scene(vbox, 800, 600);
      dialog.setScene(scene);
            

      fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

   }
   
   private void exportOutput()
   {
      LogTools.info("Exporting output");
      
      File target = fileChooser.showSaveDialog(dialog);
      
      if(target != null)
      {
         try
         {
            PrintWriter pw = new PrintWriter(target);
            for(String line : outputValues)
            {
               pw.println(line);
            }
            pw.close();
         }
         catch (FileNotFoundException e)
         {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Cannot write log");
            alert.setHeaderText(e.getMessage());
            alert.showAndWait();
         }

      }
      
      
   }
   
   @Override
   public void replaceln(String line)
   {
      Platform.runLater(() ->
      {
         outputValues.set(outputValues.size() - 1,line);
      });
   }

   @Override
   public void println(String line)
   {
      Platform.runLater(() ->
      {
         outputValues.add(line);
         output.scrollTo(outputValues.size() - 1);

      });
   }
   
   private void tryClose()
   {
      if(clients <= 0)
      {
         dialog.close();
      }
   }
   
   private void closePlatform()
   {
      clients--;
      if(clients <= 0)
      {
         close.setDisable(false);

         if(closeWhenFinished.isSelected())
         {
            dialog.close();
         }
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
         
         println(message);
         
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
         
         if(errorMessage != null)
         {
            println("[Exception] " + errorMessage);
         }
         
         if(e != null)
         {
            println("[Exception] " + e.getMessage());
            for(StackTraceElement el : e.getStackTrace())
            {
               println("[Exception] " + el.toString());
            }            
         }
         
         
         
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
         close.setDisable(true);
         dialog.show();
      });
   }

}
