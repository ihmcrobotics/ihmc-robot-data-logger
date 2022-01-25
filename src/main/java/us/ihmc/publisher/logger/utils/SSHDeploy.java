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

public class SSHDeploy extends Thread
{
   public static class SSHRemote
   {
      public final String host;
      public final String user;
      public final String password;

      public SSHRemote(String host, String user, String password)
      {
         this.host = host;
         this.user = user;
         this.password = password;
      }

   }

   private final SSHRemote remote;

   private final DeployConsoleInterface console;

   private URL deployScript;

   private final List<DeployFile> files = new ArrayList<>();
   private final List<DeployScript> scripts = new ArrayList<>();

   private final ArrayList<ImmutablePair<String, String>> variables = new ArrayList<ImmutablePair<String, String>>();

   private String customErrorMessage = null;

   public SSHDeploy(SSHRemote remote, DeployConsoleInterface console)
   {
      this.remote = remote;
      this.console = console;

   }

   /**
    * Add a file to upload The following variables will be available in the scripts ${[key]} Full path
    * to the file on the target ${[key]_NAME} Name of the file, without extension
    * 
    * @param key
    * @param source
    * @param dest
    */
   public void addBinaryFile(String key, String source, String dest)
   {
      DeployFile deployFile = new DeployFile(new File(source), dest);
      files.add(deployFile);

      variables.add(new ImmutablePair<>(key, deployFile.dest));
      variables.add(new ImmutablePair<>(key + "_NAME", deployFile.getName()));
   }

   /**
    * Add a text file to upload Before upload, the variables in this text file are replaced by their
    * values The following variables will be available in the scripts ${[key]} Full path to the file on
    * the target ${[key]_NAME} name
    * 
    * @param key
    * @param name
    * @param content
    * @param dest
    */
   public void addTextFile(String key, String name, URL content, String dest)
   {
      DeployScript script = new URLDeployScript(name, content, dest);
      scripts.add(script);

      variables.add(new ImmutablePair<>(key, script.dest));
      variables.add(new ImmutablePair<>(key + "_NAME", script.name));
   }

   public void addTextFile(String key, String name, String content, String dest)
   {
      DeployScript script = new StringDeployScript(name, dest, content);
      scripts.add(script);

      variables.add(new ImmutablePair<>(key, script.dest));
      variables.add(new ImmutablePair<>(key + "_NAME", script.name));
   }

   private void println(String line)
   {
      console.println(remote.host + ": " + line);
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
    * Deploy all files to the remote and run the command in deploy script line-by-line. Note:
    * deployScript does not run in a full shell and each command is executed as if it was running in a
    * new environment. Note that this means you cannot change the working directory with cd. Special
    * options in the script ${VAR_NAME} gets globally replaced by variable VAR_NAME Lines starting with
    * #: Comments, only get echoed to the user Lines starting with @: Hidden command, do not get shown
    * to the user. Use for passwords etc.
    *
    * @param deployScript
    */
   public void deploy(URL deployScript)
   {
      this.deployScript = deployScript;
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
            sftp.get(file, out);

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

   private void runCommand(SSHClient ssh, String commandLine) throws IOException
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
      if (trimCommand.equals("reboot"))
      {
         println("Rebooting target");
         session.exec(trimCommand);
      }
      else
      {
         try
         {
            trimCommand = trimCommand + " 2>&1";

            Command command = session.exec(trimCommand);

            InputStream is = command.getInputStream();

            int c;
            StringBuilder str = new StringBuilder();
            while ((c = is.read()) != -1)
            {
               char ch = (char) c;

               if (ch == '\n')
               {
                  println(str.toString());
                  str.setLength(0);
               }
               else
               {
                  str.append((char) c);
               }
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
                  throw new IOException(trimCommand + " returned " + command.getExitStatus());

               }
            }
         }
         finally
         {
            session.close();
         }
      }
   }

   public void run()
   {
      SSHClient ssh = new SSHClient();
      ssh.addHostKeyVerifier(new PromiscuousVerifier());
      ssh.setConnectTimeout(1000);


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

            println("Connecting as " + remote.user);
            ssh.connect(remote.host);
            ssh.authPassword(remote.user, remote.password);

            SFTPClient sftp = ssh.newSFTPClient();

            try
            {
               for (DeployFile file : files)
               {
                  runCommand(ssh, "mkdir -p " + FilenameUtils.getFullPathNoEndSeparator(file.dest));
                  println("Copying " + file.source.getAbsolutePath() + " to " + file.dest);
                  sftp.put(new FileSystemFile(file.source), file.dest);
               }

               for (DeployScript script : scripts)
               {
                  runCommand(ssh, "mkdir -p " + FilenameUtils.getFullPathNoEndSeparator(script.dest));
                  println("Copying " + script.name + " to " + script.dest);
                  script.createInputStream();
                  sftp.put(script, script.dest);

               }
            }
            finally
            {
               sftp.close();
            }

            if (deployScript != null)
            {
               InputStream is = deployScript.openStream();
               try
               {
                  List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8);

                  for (String line : lines)
                  {
                     runCommand(ssh, line);
                  }
               }
               finally
               {
                  is.close();
               }
            }
            else
            {
               println("No deploy script provided");
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

      public DeployFile(File path, String dest)
      {
         this.source = path;
         this.dest = dest;
      }

      public boolean valid()
      {
         return source.exists() && source.isFile();
      }

      public String getName()
      {
         return source.getName().substring(0, source.getName().lastIndexOf('.'));
      }

   }

   private abstract class DeployScript extends InMemorySourceFile
   {
      private final String name;
      private final String dest;

      public DeployScript(String name, String dest)
      {
         this.name = name;
         this.dest = dest;
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

      public StringDeployScript(String name, String dest, String content)
      {
         super(name, dest);
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

      public URLDeployScript(String name, URL source, String dest)
      {
         super(name, dest);
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
