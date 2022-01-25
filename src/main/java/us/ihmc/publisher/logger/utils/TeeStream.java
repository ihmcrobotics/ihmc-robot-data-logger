package us.ihmc.publisher.logger.utils;

import java.io.PrintStream;

public class TeeStream extends PrintStream
{
   private final PrintStream secondaryOuptutStream;

   public TeeStream(PrintStream first, PrintStream second)
   {
      super(first);
      this.secondaryOuptutStream = second;
   }

   public void write(byte buf[], int off, int len)
   {
      super.write(buf, off, len);

      try
      {
         secondaryOuptutStream.write(buf, off, len);
      }
      catch (Exception e)
      {
      }
   }

   public void flush()
   {
      super.flush();
      secondaryOuptutStream.flush();
   }
}