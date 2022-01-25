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

   
   public static void saveConfiguration(SSHRemote remote, CameraSettings settings, StaticHostList staticHostList, FXConsole console)
   {
      try
      {
         SSHDeploy deploy = new SSHDeploy(remote, console);
         deploy.addTextFile("CAMERA_SETTINGS", "CameraSettings.yaml", CameraSettingsLoader.toString(settings), getCameraSettingsFile(remote));
         deploy.addTextFile("STATIC_HOST_LIST", "ControllerHosts.yaml", StaticHostListLoader.toString(staticHostList), getHostsFile(remote));
         deploy.deploy(null);
      }
      catch (IOException e)
      {
         console.closeWithError(e, "Cannot save camera settings.");
      }
   }
   
   

   private static String getCameraSettingsFile(SSHRemote remote)
   {
      return "/home/" + remote.user + "/.ihmc/CameraSettings.yaml";
   }

   private static String getHostsFile(SSHRemote remote)
   {
      return "/home/" + remote.user + "/.ihmc/ControllerHosts.yaml";
   }

   public static CameraSettings loadCameraConfiguration(SSHRemote remote) throws IOException
   {
      SSHDeploy deploy = new SSHDeploy(remote, null);
      String data = deploy.download(getCameraSettingsFile(remote));

      CameraSettings settings = CameraSettingsLoader.load(data);

      return settings;

   }

   public static StaticHostList loadStaticHostList(SSHRemote remote) throws IOException
   {
      SSHDeploy deploy = new SSHDeploy(remote, null);
      String data = deploy.download(getHostsFile(remote));

      return StaticHostListLoader.loadHostList(data);

   }



   public static void deploy(SSHRemote remote, String sudo_pw, String dist, boolean restartNightly, FXConsole deployConsole)
   {
      SSHDeploy deploy = new SSHDeploy(remote, deployConsole);
      URL deployScript = loader.getResource("deploy.sh");
      URL loggerService = loader.getResource("ihmc-logger.service");
      URL crontab = loader.getResource("ihmc-logger-cron");

      
      deploy.addBinaryFile("DIST", dist, "/tmp/logger.tar.gz");
      deploy.addTextFile("LOGGER_SERVICE", "ihmc-logger.service", loggerService, "/tmp/ihmc-logger.service");
      deploy.addTextFile("CRON_ENTRY", "ihmc-logger-cron", crontab, "/tmp/ihmc-logger-cron");
      
      deploy.addVariable("NIGHTLY_RESTART", restartNightly ? "true" : "false");
      deploy.addVariable("SUDO_PW", sudo_pw);


      
      deploy.deploy(deployScript);
   }
}
