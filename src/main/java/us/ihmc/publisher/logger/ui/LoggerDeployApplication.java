package us.ihmc.publisher.logger.ui;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Date;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import us.ihmc.publisher.logger.utils.TeeStream;

public class LoggerDeployApplication extends Application
{
   private static final URL uiDescription = LoggerDeployApplication.class.getResource("LoggerSetup.fxml");

   /**
    * Helper function to open this as part of another application
    * 
    * @param parameters Parameters from application start
    * @param scene      Parent scene
    */
   public static void open(String loggerDistribution, LoggerDeployScript deployScript, Scene scene)
   {
      try
      {
         FXMLLoader loader = new FXMLLoader(uiDescription);
         Parent root = loader.load();
         
         LoggerDeployController controller = loader.getController();
         controller.setLoggerDistribution(loggerDistribution);
         controller.setDeployScript(deployScript);
         
         Stage stage = new Stage();
         stage.setTitle("Logger deployment");
         stage.initOwner(scene.getWindow());
         stage.initModality(Modality.APPLICATION_MODAL);

         stage.setScene(new Scene(root, 1280, 900));
         stage.show();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   @Override
   public void start(Stage stage) throws IOException
   {

      redirectOutput();

      FXMLLoader loader = new FXMLLoader(uiDescription);
      Parent root = loader.load();

      LoggerDeployController controller = loader.getController();
      controller.setDeployScript(new LoggerDeployScript(){});

      
      controller.setLoggerDistribution(getParameters().getNamed().get("logger-dist"));

      Scene scene = new Scene(root, 1280, 900);
      stage.setTitle("Logger deployment");
      stage.setScene(scene);
      stage.show();

   }

   private void redirectOutput() throws FileNotFoundException
   {
      @SuppressWarnings("resource")
      PrintStream log = new PrintStream(new FileOutputStream("logger-deploy.log", true));

      TeeStream stdOutStream = new TeeStream(System.out, log);
      TeeStream stdErrStream = new TeeStream(System.err, log);

      System.setOut(stdOutStream);
      System.setErr(stdErrStream);

      System.out.println("--- " + new Date().toString() + " ---");
   }

   public static void main(String[] args)
   {
      launch(args);
   }
}