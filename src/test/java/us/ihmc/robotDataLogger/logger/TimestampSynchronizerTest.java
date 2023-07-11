package us.ihmc.robotDataLogger.logger;

import gnu.trove.list.array.TLongArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.ihmc.robotDataLogger.LogProperties;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Objects;

public class TimestampSynchronizerTest
{

   private static long[] robotTimestamps;
   private static long[] systemNanoTime;

   private BlackmagicVideoDataLogger blackmagicVideoDataLogger;

   YoVariableLoggerOptions options;


   private LogProperties logProperties = new LogPropertiesWriter(new File("//", ""));

   public TimestampSynchronizerTest() throws IOException
   {
      options = new YoVariableLoggerOptions();
      blackmagicVideoDataLogger = new BlackmagicVideoDataLogger("Test", new File("us.ihmc.robotDataLogger/logging/Test"), logProperties, 0, options, true);
   }

   @BeforeEach
   public void testLoadingControllerTimestamps() throws URISyntaxException, IOException
   {
      File timestampFile = new File(TimestampSynchronizerTest.class.getClassLoader().getResource("us.ihmc.robotDataLogger/logging/ControllerTimestamps.dat").toURI());

      parseTimestampData(timestampFile);

   }

   private void parseTimestampData(File timestampFile) throws IOException
   {
      try (BufferedReader bufferedReader = new BufferedReader(new FileReader(timestampFile)))
      {
         String line;

         TLongArrayList robotTimestamps = new TLongArrayList();
         TLongArrayList videoTimestamps = new TLongArrayList();

         while ((line = bufferedReader.readLine()) != null)
         {
            String[] stamps = line.split("\\s");
            long robotStamp = Long.parseLong(stamps[0]);
            long videoStamp = Long.parseLong(stamps[1]);

            robotTimestamps.add(robotStamp);
            videoTimestamps.add(videoStamp);
         }

         this.robotTimestamps = robotTimestamps.toArray();
         this.systemNanoTime = videoTimestamps.toArray();
      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }


   @Test
   public void testGettingData()
   {
      for (int i = 0; i < robotTimestamps.length; i++)
      {
         System.out.println(robotTimestamps[i]);
      }
   }

   @Test
   public void testStoringData()
   {
      for (int i = 0; i < robotTimestamps.length; i++)
      {
         blackmagicVideoDataLogger.timestampChanged(robotTimestamps[i]);
      }

      for (int i = 0; i < blackmagicVideoDataLogger.timestampSynchronizer.getTimestamps().size(); i++)
      {
         System.out.println(blackmagicVideoDataLogger.timestampSynchronizer.getTimestamps().get(i));
      }
   }

}
