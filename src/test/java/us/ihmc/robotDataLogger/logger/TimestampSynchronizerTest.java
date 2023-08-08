package us.ihmc.robotDataLogger.logger;

import gnu.trove.list.array.TLongArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URISyntaxException;

public class TimestampSynchronizerTest
{
   private static long[] controllerTimestamps;

   private final BlackmagicVideoDataLogger.TimestampAdjuster timestampAdjuster;

   public TimestampSynchronizerTest()
   {
      timestampAdjuster = new BlackmagicVideoDataLogger.TimestampAdjuster();
   }

   @BeforeEach
   public void loadControllerTimestamps() throws URISyntaxException, IOException
   {
      File timestampFile = new File(TimestampSynchronizerTest.class.getClassLoader().getResource("us.ihmc.robotDataLogger/logging/ControllerTimestamps.dat").toURI());

      try (BufferedReader bufferedReader = new BufferedReader(new FileReader(timestampFile)))
      {
         String line;

         TLongArrayList controllerTimestamps = new TLongArrayList();

         while ((line = bufferedReader.readLine()) != null)
         {
            String[] stamps = line.split("\\s");
            long robotStamp = Long.parseLong(stamps[0]);

            controllerTimestamps.add(robotStamp);
         }

         TimestampSynchronizerTest.controllerTimestamps = controllerTimestamps.toArray();
      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }

   @Test
   public void testGettingData()
   {
      for (int i = 0; i < controllerTimestamps.length; i++)
      {
         System.out.println(controllerTimestamps[i]);
      }
   }

//   @Test
//   public void testStoringData()
//   {
//      for (int i = 0; i < controllerTimestamps.length; i++)
//      {
//         timestampAdjuster.addToCircularMap(System.nanoTime(), controllerTimestamps[i]);
//      }
//
//      for (int i = 0; i < timestampAdjuster.getControllerTimestamps().size(); i++)
//      {
//         System.out.println(timestampAdjuster.getControllerTimestamps().get(i));
//      }
//   }


   @Test
   public void testDiffBetweenComputers()
   {
      for (int i = 0; i < controllerTimestamps.length; i++)
      {
         timestampAdjuster.computeDifferenceInMachineTimes(System.nanoTime(), controllerTimestamps[i]);
         // capture is null so we can't get the hardwareTime, this is a problem
         // could use system.nanoTime() as my hardware time, from the local computer
//         blackmagicVideoDataLogger.timestampChanged(robotTimestamps[i]);
      }

//      //Not using the timestampchanged method at all, need to figure that out...
//      assertTrue(blackmagicVideoDataLogger.timestampSynchronizer.getCurrentDiffBetweenControllerAndLogger() > 0);
   }
}
