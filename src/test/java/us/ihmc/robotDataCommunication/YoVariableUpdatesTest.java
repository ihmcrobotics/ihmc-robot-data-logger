package us.ihmc.robotDataCommunication;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.robotDataLogger.util.JVMStatisticsGenerator;
import us.ihmc.yoVariables.parameters.DoubleParameter;
import us.ihmc.yoVariables.parameters.XmlParameterReader;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;

//TODO This entire test seems like a bad setup because there isn't any sort of check to see if its working. Also when you change the value of a YoVariable,
//TODO it updates automatically so the only way to check something would be on the client side, and there isn't a client in this test.
//TODO Somehow this could incorporate a test with a thread but currently it seems like its a bad test that is now at the very least commented!

public class YoVariableUpdatesTest
{
   enum TestEnum
   {
      A, B, C, D
   }

   private XmlParameterReader parameterReader;
   private final YoRegistry registry = new YoRegistry("YoVariableUpdatesTest");
   private final YoInteger timeout = new YoInteger("timeout", registry);

   private final YoDouble variableDoubleOne = new YoDouble("variableDoubleOne", registry);
   private final YoDouble variableDoubleTwo = new YoDouble("variableDoubleTwo", registry);
   private final YoInteger variableIntegerThree = new YoInteger("variableIntegerThree", registry);
   private final YoEnum<TestEnum> variableEnumFour = new YoEnum<>("variableEnumFour", "", registry, TestEnum.class, true);

   private final YoInteger echoIn = new YoInteger("echoIn", registry);
   private final YoInteger echoOut = new YoInteger("echoOut", registry);

   private final YoBoolean startVariableSummary = new YoBoolean("startVariableSummary", registry);

   private final DoubleParameter parameter1 = new DoubleParameter("param1", registry);
   private final DoubleParameter parameter2 = new DoubleParameter("param2", registry);

   private final YoDouble parameter1Echo = new YoDouble("param1Echo", registry);
   private final YoDouble parameter2Echo = new YoDouble("param2Echo", registry);

   private final YoVariableServer server = new YoVariableServer(getClass(),null, new DataServerSettings(false), 0.001);
   private JVMStatisticsGenerator jvmStatisticsGenerator;

   private long timestamp = 0;

   @Test
   public void testYoVariableConnections() throws IOException
   {
      Stopwatch timerToEndLoop = new Stopwatch();
      timerToEndLoop.start();

      parameterReader = new XmlParameterReader(getClass().getResourceAsStream("TestParameters.xml"));

      // Sets the main registry for the server, and adds a JVMStatisticsGenerator to the server
      server.setMainRegistry(registry, null);
      jvmStatisticsGenerator = new JVMStatisticsGenerator(server);

      // Creates a list on the server that will store the variables added to it, this appears to be for viewing purposes as the only
      // data that gets stored are the string names, not the actual value
      server.createSummary("YoVariableUpdatesTest.startVariableSummary");
      server.addSummarizedVariable("YoVariableUpdatesTest.variableDoubleOne");
      server.addSummarizedVariable("YoVariableUpdatesTest.variableDoubleTwo");
      server.addSummarizedVariable(variableIntegerThree);

      jvmStatisticsGenerator.addVariablesToStatisticsGenerator(server);
      startVariableSummary.set(false);
      jvmStatisticsGenerator.start();
      parameterReader.readParametersInRegistry(registry);

      // Starts a thread that has its own YoVariables and starts the server
      new ThreadTester(server).start();
      server.start();

      variableIntegerThree.set(5000);

      // Sets the timeout value because when declaring a YoVariable the user isn't allowed to set the value, it has to be done after
      timeout.set(1);

      // Values needed to manipulate the Enum YoVariables, ensures the Enum is randomly changed in the while loop
      int i = 0;
      TestEnum[] values = {TestEnum.A, TestEnum.B, TestEnum.C, TestEnum.D};

      // Loop runs for around 10 seconds to ensure variables are getting updated, but doesn't run infinitely
      while (timerToEndLoop.totalElapsed() < 12)
      {
         // Adding values to different YoVariables to see if the updates will go through
         variableDoubleOne.add(1.0);
         variableDoubleTwo.sub(1.0);
         variableIntegerThree.sub(1);

         // Checks to see if the Enum index is too high and resets to start the Enum index over again, ensures random Enum values
         if (++i >= values.length)
            i = 0;

         variableEnumFour.set(values[i]);

         echoOut.set(echoIn.getIntegerValue());

         // These values should be set to the values of param1 and param2
         parameter1Echo.set(parameter1.getValue());
         parameter2Echo.set(parameter2.getValue());

         // Sends the updated variables to the server, the timestamp is used to correlate with the client and how long it took to get the data
         server.update(timestamp);

         // getting updates correctly
         timestamp += Conversions.millisecondsToNanoseconds(1);
         ThreadTools.sleep(timeout.getIntegerValue());
      }

      // The server needs to close because if another test or class wants to start a server using localhost, it will fail because its already in use
      server.close();
   }

   private class ThreadTester extends Thread
   {
      private final YoRegistry registry = new YoRegistry("ThreadServer");
      private final YoDouble A = new YoDouble("A", registry);
      private final YoDouble B = new YoDouble("B", registry);
      private final YoDouble C = new YoDouble("C", registry);

      private final YoEnum<TestEnum> echoThreadIn = new YoEnum<>("echoThreadIn", registry, TestEnum.class, false);
      private final YoEnum<TestEnum> echoThreadOut = new YoEnum<>("echoThreadOut", registry, TestEnum.class, false);

      private final DoubleParameter param1 = new DoubleParameter("threadParam1", registry);
      private final DoubleParameter param2 = new DoubleParameter("threadParam2", registry);

      private final YoDouble param1Echo = new YoDouble("threadParam1Echo", registry);
      private final YoDouble param2Echo = new YoDouble("threadParam2Echo", registry);

      public ThreadTester(YoVariableServer server)
      {
         server.addRegistry(registry, null);
         parameterReader.readParametersInRegistry(registry);
      }

      @Override
      public void run()
      {
         // This will continuously update the variables used on the thread and send them to the server
         while (true)
         {
            A.set(A.getDoubleValue() + 0.5);
            B.set(B.getDoubleValue() - 0.5);
            C.set(C.getDoubleValue() * 2.0);

            echoThreadOut.set(echoThreadIn.getEnumValue());

            param1Echo.set(param1.getValue());
            param2Echo.set(param2.getValue());

            server.update(timestamp, registry);

            ThreadTools.sleep(10);
         }
      }
   }
}
