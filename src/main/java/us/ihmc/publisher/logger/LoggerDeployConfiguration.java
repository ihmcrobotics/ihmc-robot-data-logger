package us.ihmc.publisher.logger;

import java.io.IOException;
import java.net.URL;

import us.ihmc.publisher.logger.utils.SSHDeploy;
import us.ihmc.publisher.logger.utils.SSHDeploy.SSHRemote;
import us.ihmc.publisher.logger.utils.ui.FXConsole;
import us.ihmc.robotDataLogger.CameraSettings;
import us.ihmc.robotDataLogger.CameraSettingsLoader;
import us.ihmc.robotDataLogger.StaticHostList;
import us.ihmc.robotDataLogger.StaticHostListLoader;

public class LoggerDeployConfiguration
{
   private final static Class<?> loader = LoggerDeployConfiguration.class;

   public static void saveConfiguration(SSHRemote remote, CameraSettings settings, StaticHostList staticHostList, boolean restartonSave, FXConsole console)
   {
      try
      {
         SSHDeploy deploy = new SSHDeploy(remote, console);
         deploy.addVariable("RESTART_LOGGER", restartonSave ? "true" : "false");
         deploy.addTextFile("CAMERA_SETTINGS", "CameraSettings.yaml", CameraSettingsLoader.toString(settings), getCameraSettingsFile(remote), false);
         deploy.addTextFile("STATIC_HOST_LIST", "ControllerHosts.yaml", StaticHostListLoader.toString(staticHostList), getHostsFile(remote), false);
         deploy.deploy("if ${RESTART_LOGGER}; then sudo /bin/systemctl restart ihmc-logger.service; echo \"Restarted logger\"; else echo \"Skipped logger restart\"; fi");
      }
      catch (IOException e)
      {
         console.closeWithError(e, "Cannot save camera settings.");
      }
   }

   public static String getCameraSettingsFile(SSHRemote remote)
   {
      return "/home/" + remote.user + "/.ihmc/CameraSettings.yaml";
   }

   public static String getHostsFile(SSHRemote remote)
   {
      return "/home/" + remote.user + "/.ihmc/ControllerHosts.yaml";
   }

   public static CameraSettings loadCameraConfiguration(SSHRemote remote) throws IOException
   {
      SSHDeploy deploy = new SSHDeploy(remote, null);
      String data = deploy.download(getCameraSettingsFile(remote));

      return CameraSettingsLoader.load(data);
   }

   public static StaticHostList loadStaticHostList(SSHRemote remote) throws IOException
   {
      SSHDeploy deploy = new SSHDeploy(remote, null);
      String data = deploy.download(getHostsFile(remote));

      return StaticHostListLoader.loadHostList(data);
   }

   public static void deploy(SSHRemote remote, String dist, boolean restartNightly, FXConsole deployConsole, boolean logger_service)
   {
      SSHDeploy deploy = new SSHDeploy(remote, deployConsole);
      URL deployScript = loader.getResource("deploy.sh");
      URL loggerService = loader.getResource("ihmc-logger.service");
      URL crontab = loader.getResource("ihmc-logger-cron");
      
      deploy.addBinaryFile("DIST", dist, "/tmp/logger.tar", false);
      deploy.addTextFile("LOGGER_SERVICE", "ihmc-logger.service", loggerService, "/etc/systemd/system/ihmc-logger.service", true);
      deploy.addTextFile("CRON_ENTRY", "ihmc-logger-cron", crontab, "/tmp/ihmc-logger-cron", true);
      
      deploy.addVariable("NIGHTLY_RESTART", restartNightly ? "true" : "false");
      deploy.addVariable("DEPLOY_SERVICE", logger_service ? "true" : "false");

      deploy.deploy(deployScript);
   }
}
