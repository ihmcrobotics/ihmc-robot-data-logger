package us.ihmc.publisher.logger.ui;

import java.io.IOException;

import javafx.stage.Stage;
import us.ihmc.publisher.logger.LoggerDeployConfiguration;
import us.ihmc.publisher.logger.utils.SSHDeploy.SSHRemote;
import us.ihmc.publisher.logger.utils.ui.FXConsole;
import us.ihmc.robotDataLogger.CameraSettings;
import us.ihmc.robotDataLogger.StaticHostList;

public interface LoggerDeployScript
{
   /**
    * Called when clicking "Deploy logger" in the application
    *
    * @param logger_host
    * @param logger_user
    * @param logger_password
    * @param logger_sudo_password
    * @param logger_dist
    * @param nightly_restart
    * @param stage
    * @param logger_service
    */
   default void deploy(String logger_host,
                       String logger_user,
                       String logger_password,
                       String logger_sudo_password,
                       String logger_dist,
                       boolean nightly_restart,
                       Stage stage,
                       boolean logger_service,
                       boolean deploy_with_lock_file)
   {
      FXConsole deployConsole = new FXConsole(stage);
      SSHRemote remote = new SSHRemote(logger_host, logger_user, logger_password, logger_sudo_password);
      LoggerDeployConfiguration.deploy(remote, logger_dist, nightly_restart, deployConsole, logger_service, deploy_with_lock_file);
   }

   default boolean implementsAutoRestart()
   {
      return true;
   }

   default void saveConfiguration(String logger_host,
                                  String logger_user,
                                  String logger_password,
                                  String logger_sudo_password,
                                  CameraSettings settings,
                                  StaticHostList staticHostList,
                                  boolean restartonSave,
                                  Stage stage)
   {
      FXConsole deployConsole = new FXConsole(stage);
      SSHRemote remote = new SSHRemote(logger_host, logger_user, logger_password, logger_sudo_password);

      LoggerDeployConfiguration.saveConfiguration(remote, settings, staticHostList, restartonSave, deployConsole);
   }

   default CameraSettings loadCameraConfiguration(String logger_host, String logger_user, String logger_password, String logger_sudo_password, Stage stage)
         throws IOException
   {
      SSHRemote remote = new SSHRemote(logger_host, logger_user, logger_password, logger_sudo_password);
      return LoggerDeployConfiguration.loadCameraConfiguration(remote);
   }

   default StaticHostList loadStaticHostList(String logger_host, String logger_user, String logger_password, String logger_sudo_password, Stage stage)
         throws IOException
   {
      SSHRemote remote = new SSHRemote(logger_host, logger_user, logger_password, logger_sudo_password);
      return LoggerDeployConfiguration.loadStaticHostList(remote);
   }
}