package us.ihmc.publisher.logger.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.InMemoryDestFile;
import net.schmizz.sshj.xfer.InMemorySourceFile;

/**
 * Utility class to deploy scripts and files to a remote SSH server
 * 
 * Scripts have variable substituation for ${VAR_NAME}. The following variables are pre-defined
 * 
 * ${HOME}: Home directory of SSH user
 * 
 * @author Jesper Smith
 *
 */
public class SSHDeploy extends Thread
{
   public static class SSHRemote
   {
      public final String host;
      public final String user;
      public final String password;
      public final String sudoPassword;

      public boolean allowRootLogin = false;

      public SSHRemote(String host, String user, String password, String sudoPassword)
      {
         this.host = host;
         this.user = user;
         this.password = password;
         this.sudoPassword = sudoPassword;
      }

      public void setAllowRootLogin(boolean allowRootLogin)
      {
         this.allowRootLogin = allowRootLogin;
      }
   }

   private final SSHRemote remote;

   private final DeployConsoleInterface console;

   private final String homeDirectory;

   private DeployScript deployScript;

   private final List<DeployFile> files = new ArrayList<>();
   private final List<DeployScript> scripts = new ArrayList<>();

   private final ArrayList<ImmutablePair<String, String>> variables = new ArrayList<>();

   private String customErrorMessage = null;

   private boolean isRebooted = false;

   public SSHDeploy(SSHRemote remote, DeployConsoleInterface console)
   {
      this.remote = remote;
      this.console = console;

      if (remote.user.equals("root"))
      {
         homeDirectory = "/root";
      }
      else
      {
         homeDirectory = "/home/" + remote.user;
      }

      addVariable("HOME", homeDirectory);
      addVariable("USER", remote.user);
   }

   /**
    * Expand destination folder with the home directory
    * 
    * @param dest
    * @return home directory
    */
   private String expandFilename(String dest)
   {
      dest = dest.trim();
      if (dest.startsWith("~/"))
      {
         return homeDirectory + "/" + dest.substring(2);
      }
      else
      {
         return dest;
      }
   }

   /**
    * Add a file to upload The following variables will be available in the scripts ${[key]} Full path
    * to the file on the target ${[key]_NAME} Name of the file, without extension
    *
    * If the dest start with ~/ it gets replaced with the home directory of the SSH server
    * 
    * @param key
    * @param source
    * @param dest
    */
   public void addBinaryFile(String key, String source, String dest, boolean needRootPermissions)
   {
      DeployFile deployFile = new DeployFile(new File(source), expandFilename(dest), needRootPermissions);
      files.add(deployFile);

      addVariable(key, deployFile.dest);
      addVariable(key + "_NAME", deployFile.getName());
   }

   /**
    * Add a text file to upload Before upload, the variables in this text file are replaced by their
    * values The following variables will be available in the scripts ${[key]} Full path to the file on
    * the target ${[key]_NAME} name
    * 
    * If the dest start with ~/ it gets replaced with the home directory of the SSH server
    * 
    * @param key
    * @param name
    * @param content
    * @param dest
    */
   public void addTextFile(String key, String name, URL content, String dest, boolean needRootPermissions)
   {
      DeployScript script = new URLDeployScript(name, content, expandFilename(dest), needRootPermissions);
      scripts.add(script);

      addVariable(key, script.dest);
      addVariable(key + "_NAME", script.name);
   }

   /**
    * Add a text file to upload Before upload, the variables in this text file are replaced by their
    * values The following variables will be available in the scripts ${[key]} Full path to the file on
    * the target ${[key]_NAME} name
    * 
    * If the dest start with ~/ it gets replaced with the home directory of the SSH server
    * 
    * @param key
    * @param name
    * @param content
    * @param dest
    */
   public void addTextFile(String key, String name, String content, String dest, boolean needRootPermissions)
   {
      DeployScript script = new StringDeployScript(name, expandFilename(dest), content, needRootPermissions);
      scripts.add(script);

      addVariable(key, script.dest);
      addVariable(key + "_NAME", script.name);
   }

   private void println(String line)
   {
      console.println(remote.host + ": " + line);
   }

   private void replaceln(String line)
   {
      console.replaceln(remote.host + ": " + line);
   }

   /**
    * Add a variable for the deploy script and text files
    * 
    * @param key
    * @param value
    */
   public void addVariable(String key, String value)
   {
      variables.add(new ImmutablePair<>(key, value));
   }

   /**
    * Deploy all files to the remote and run the command in deploy script line-by-line. 
    * 
    * Note: deployScript does not run in a full shell and each command is executed as if it was running in a
    * new environment. Note that this means you cannot change the working directory with cd. 
    * 
    * 
    * Special options in the script 
    *  
    * ${VAR_NAME} gets globally replaced by variable VAR_NAME 
    * Lines starting with #: Comments, only get echoed to the user 
    * Lines starting with @: Hidden command, do not get shown to the user. Use for passwords etc.
    *
    * @param deployScript
    */
   public void deploy(URL deployScript)
   {
      // Re-use deploy script here to be able to use different styles of inputs in deploy()
      // Set unused fields to null
      this.deployScript = new URLDeployScript(null, deployScript, null, false);
      start();
   }

   /**
    * Deploy all files to the remote and run the command in deploy script line-by-line. 
    * 
    * Note: deployScript does not run in a full shell and each command is executed as if it was running in a
    * new environment. Note that this means you cannot change the working directory with cd. 
    * 
    * 
    * Special options in the script 
    *  
    * ${VAR_NAME} gets globally replaced by variable VAR_NAME 
    * Lines starting with #: Comments, only get echoed to the user 
    * Lines starting with @: Hidden command, do not get shown to the user. Use for passwords etc.
    *
    * @param deployScript
    */
   public void deploy(String deployScript)
   {
      // Re-use deploy script here to be able to use different styles of inputs in deploy(). 
      // Set unused fields to null
      this.deployScript = new StringDeployScript(null, null, deployScript, false);
      start();
   }

   public String download(String file) throws IOException
   {
      SSHClient ssh = new SSHClient();
      ssh.addHostKeyVerifier(new PromiscuousVerifier());
      ssh.setConnectTimeout(1000);
      ssh.connect(remote.host);

      try
      {

         ssh.authPassword(remote.user, remote.password);

         SFTPClient sftp = ssh.newSFTPClient();

         try
         {

            DownloadFile out = new DownloadFile();
            sftp.get(expandFilename(file), out);

            return out.toString();

         }
         finally
         {
            sftp.close();
         }
      }
      finally
      {
         ssh.close();
      }

   }

   private String replaceAllVariables(String original)
   {
      String modified = original;
      for (ImmutablePair<String, String> p : variables)
      {
         modified = modified.replaceAll("\\$\\{" + p.getLeft() + "\\}", Matcher.quoteReplacement(p.getRight()));
      }
      return modified;
   }

   private void runCommand(SSHClient ssh, String commandLine, boolean runAsRoot) throws IOException
   {
      String trimCommand = commandLine.trim();

      if (trimCommand.isEmpty())
      {
         return;
      }

      trimCommand = replaceAllVariables(trimCommand);

      boolean hideCommand = false;
      if (trimCommand.startsWith("@"))
      {
         // @ hides the command from the user
         trimCommand = trimCommand.substring(1);
         hideCommand = true;
      }
      else
      {
         println(trimCommand);
      }

      if (trimCommand.startsWith("#"))
      {
         return;
      }

      Session session = ssh.startSession();
      if (trimCommand.equals("reboot") || trimCommand.equals("sudo reboot"))
      {
         println("Rebooting target");
         isRebooted = true;

         if ("root".equals(remote.user))
         {
            session.exec("reboot");
         }
         else
         {
            session.exec("echo " + remote.sudoPassword + " | sudo -S reboot");
         }
      }
      else
      {
         try
         {
            String echoCommand = trimCommand;

            if (runAsRoot)
            {
               if (!trimCommand.startsWith("sudo"))
               {
                  trimCommand = "sudo " + trimCommand;
               }
            }

            if (trimCommand.contains("sudo "))
            {
               trimCommand = trimCommand.replaceAll("sudo ", "echo " + remote.sudoPassword + " | sudo -S ");
            }

            trimCommand = trimCommand + " 2>&1";

            Command command = session.exec(trimCommand);

            InputStream is = command.getInputStream();

            int c;
            StringBuilder str = new StringBuilder();
            while ((c = is.read()) != -1)
            {
               char ch = (char) c;

               if (Character.isISOControl(ch))
               {
                  if (ch == '\n')
                     println(str.toString());
                  else
                     replaceln(str.toString());
                  str.setLength(0);
               }
               else
               {
                  str.append((char) c);
               }
               System.out.print(ch);
            }

            command.join(5, TimeUnit.SECONDS);

            if (command.getExitStatus() != 0)
            {
               if (hideCommand)
               {
                  throw new IOException("Last command returned " + command.getExitStatus());
               }
               else
               {
                  throw new IOException(echoCommand + " returned " + command.getExitStatus());

               }
            }
         }
         finally
         {
            session.close();
         }
      }
   }

   private String createTempPath()
   {
      return "/tmp/" + java.util.UUID.randomUUID().toString() + ".tmp";
   }

   public void run()
   {
      SSHClient ssh = new SSHClient();
      ssh.addHostKeyVerifier(new PromiscuousVerifier());
      ssh.setConnectTimeout(1000);

      isRebooted = false;

      console.open();

      try
      {
         try
         {

            for (DeployFile file : files)
            {
               if (!file.valid())
               {
                  throw new IOException("Cannot find " + file.source.getAbsolutePath());
               }
            }

            if ("root".equals(remote.user) && !remote.allowRootLogin)
            {
               throw new IOException("Cannot login as root. Use normal user account instead");
            }

            println("Connecting as " + remote.user);
            ssh.connect(remote.host);
            ssh.authPassword(remote.user, remote.password);

            SFTPClient sftp = ssh.newSFTPClient();

            try
            {
               for (DeployFile file : files)
               {

                  String tempPath = createTempPath();

                  println("Copying " + file.source.getAbsolutePath() + " to " + file.dest);
                  sftp.put(new FileSystemFile(file.source), tempPath);

                  runCommand(ssh, "mkdir -p " + FilenameUtils.getFullPathNoEndSeparator(file.dest), file.needRootPermissions);
                  runCommand(ssh, "mv " + tempPath + " " + file.dest, file.needRootPermissions);
               }

               for (DeployScript script : scripts)
               {
                  runCommand(ssh, "mkdir -p " + FilenameUtils.getFullPathNoEndSeparator(script.dest), script.needsRootPermission);
                  println("Copying " + script.name + " to " + script.dest);

                  script.createInputStream();

                  String tempPath = createTempPath();
                  sftp.put(script, tempPath);
                  runCommand(ssh, "mv " + tempPath + " " + script.dest, script.needsRootPermission);

               }

            }
            finally
            {
               sftp.close();
            }

            if (deployScript != null)
            {
               deployScript.createInputStream();
               try
               {
                  List<String> lines = IOUtils.readLines(deployScript.getInputStream(), StandardCharsets.UTF_8);

                  for (String line : lines)
                  {
                     runCommand(ssh, line, false);
                  }
               }
               finally
               {
                  deployScript.getInputStream().close();
               }
            }
            else
            {
               println("No deploy script provided");
            }

            if (!isRebooted)
            {
               runCommand(ssh, "sync", false);
            }
         }
         finally
         {
            ssh.close();
         }

         println("Finished deploy");
         console.close();

      }
      catch (IOException e)
      {
         console.closeWithError(e, customErrorMessage);
      }

   }

   private static class DeployFile
   {
      private final File source;
      private final String dest;
      private final boolean needRootPermissions;

      public DeployFile(File path, String dest, boolean needRootPermissions)
      {
         this.source = path;
         this.dest = dest;
         this.needRootPermissions = needRootPermissions;
      }

      public boolean valid()
      {
         return source.exists() && source.isFile();
      }

      public String getName()
      {
         if (valid())
         {
            return source.getName().substring(0, source.getName().lastIndexOf('.'));
         }
         else
         {
            return source.getName();
         }
      }

   }

   private abstract class DeployScript extends InMemorySourceFile
   {
      private final String name;
      private final String dest;
      private final boolean needsRootPermission;

      public DeployScript(String name, String dest, boolean needsRootPermission)
      {
         this.name = name;
         this.dest = dest;
         this.needsRootPermission = needsRootPermission;
      }

      @Override
      public String getName()
      {
         return name;
      }

      public abstract void createInputStream() throws IOException;

   }

   private class StringDeployScript extends DeployScript
   {

      private final String content;
      private InputStream inputStream;

      public StringDeployScript(String name, String dest, String content, boolean needRootPermission)
      {
         super(name, dest, needRootPermission);
         this.content = content;
      }

      @Override
      public long getLength()
      {
         return content.length();
      }

      @Override
      public InputStream getInputStream() throws IOException
      {
         return inputStream;
      }

      @Override
      public void createInputStream() throws IOException
      {
         String newContent = replaceAllVariables(content);
         inputStream = org.apache.commons.io.IOUtils.toInputStream(newContent, StandardCharsets.UTF_8);

      }

   }

   private class URLDeployScript extends DeployScript
   {
      private final URL source;

      private int size;
      private InputStream outputStream;

      public URLDeployScript(String name, URL source, String dest, boolean needRootPermissions)
      {
         super(name, dest, needRootPermissions);
         this.source = source;
      }

      public void createInputStream() throws IOException
      {
         if (source == null)
         {
            throw new IOException("Source for " + getName() + " is null");
         }

         String content = org.apache.commons.io.IOUtils.toString(source, StandardCharsets.UTF_8);
         content = replaceAllVariables(content);
         outputStream = org.apache.commons.io.IOUtils.toInputStream(content, StandardCharsets.UTF_8);
      }

      @Override
      public long getLength()
      {
         return size;
      }

      @Override
      public InputStream getInputStream() throws IOException
      {
         return outputStream;
      }

   }

   private class DownloadFile extends InMemoryDestFile
   {
      private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

      public String toString()
      {
         try
         {
            return stream.toString(StandardCharsets.UTF_8.name());
         }
         catch (UnsupportedEncodingException e)
         {
            throw new RuntimeException(e);
         }
      }

      @Override
      public OutputStream getOutputStream() throws IOException
      {
         return stream;
      }

   }

   /**
    * Set a custom error message to display if a command in the deploy script fails.
    * 
    * @param msg
    */
   public void setErrorMessage(String msg)
   {
      customErrorMessage = msg;
   }

}
