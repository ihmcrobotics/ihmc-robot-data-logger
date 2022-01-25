package us.ihmc.publisher.logger.utils;

public interface DeployConsoleInterface
{
   void open();
   
   void closeWithError(Exception e, String customErrorMessage);

   void closeWithMessage(String message);
   
   void close();

   void println(String line);

}
