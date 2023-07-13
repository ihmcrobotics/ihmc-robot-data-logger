package us.ihmc.robotDataLogger.example;
import java.io.File;
import java.io.IOException;

import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.robotDataLogger.YoVariableServer;
import us.ihmc.robotDataLogger.logger.DataServerSettings;
import us.ihmc.robotDataLogger.memoryLogger.CircularMemoryLogger;
import us.ihmc.robotDataLogger.util.JVMStatisticsGenerator;
import us.ihmc.yoVariables.parameters.DoubleParameter;
import us.ihmc.yoVariables.parameters.XmlParameterReader;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;

public class MemoryLoggerExample
{
   enum TestEnum
   {
      A, B, C, D
   }

   private final XmlParameterReader parameterReader;

   private final YoRegistry registry = new YoRegistry("tester");
   private final YoDouble var1 = new YoDouble("var1", registry);
   private final YoDouble var2 = new YoDouble("var2", registry);
   private final YoInteger var4 = new YoInteger("var4", registry);
   private final YoEnum<TestEnum> var3 = new YoEnum<>("var3", "", registry, TestEnum.class, true);

   private final YoBoolean stop = new YoBoolean("stop", registry);
   
   private final YoInteger echoIn = new YoInteger("echoIn", registry);
   private final YoInteger echoOut = new YoInteger("echoOut", registry);

   private final YoInteger timeout = new YoInteger("timeout", registry);

   private final YoBoolean startVariableSummary = new YoBoolean("startVariableSummary", registry);
   private final YoBoolean gc = new YoBoolean("gc", registry);

   private final DoubleParameter param1 = new DoubleParameter("param1", registry);
   private final DoubleParameter param2 = new DoubleParameter("param2", registry);

   private final YoDouble param1Echo = new YoDouble("param1Echo", registry);
   private final YoDouble param2Echo = new YoDouble("param2Echo", registry);

   private final YoVariableServer server = new YoVariableServer(getClass(),
                                                                null,
                                                                new DataServerSettings(false),
                                                                0.001);
   private final JVMStatisticsGenerator jvmStatisticsGenerator;

   private volatile long timestamp = 0;

   public MemoryLoggerExample() throws IOException
   {
      new YoInteger("var5", registry);
      new YoEnum<>("var6", "", registry, TestEnum.class, true);
      parameterReader = new XmlParameterReader(getClass().getResourceAsStream("TestParameters.xml"));

      server.addBufferListener(new CircularMemoryLogger(new File("/tmp"), 60 * 1000));
      
      server.setMainRegistry(registry, null);
      jvmStatisticsGenerator = new JVMStatisticsGenerator(server);

      server.createSummary("tester.startVariableSummary");
      server.addSummarizedVariable("tester.var1");
      server.addSummarizedVariable("tester.var2");
      server.addSummarizedVariable(var4);

      jvmStatisticsGenerator.addVariablesToStatisticsGenerator(server);

      startVariableSummary.set(false);

      jvmStatisticsGenerator.start();

      parameterReader.readParametersInRegistry(registry);

      ThreadTester tester = new ThreadTester(server);
      tester.start();
      
      server.start();
      var4.set(5000);

      timeout.set(1);

      int i = 0;
      TestEnum[] values = {TestEnum.A, TestEnum.B, TestEnum.C, TestEnum.D};
      while (!stop.getValue())
      {
         var1.add(1.0);
         var2.sub(1.0);
         var4.sub(1);

         if (++i >= values.length)
         {
            i = 0;
         }
         var3.set(values[i]);

         //         var5.set(new Random().nextInt());

         echoOut.set(echoIn.getIntegerValue());

         if (gc.getBooleanValue())
         {
            System.gc();
            gc.set(false);
         }

         param1Echo.set(param1.getValue());
         param2Echo.set(param2.getValue());

         timestamp += Conversions.millisecondsToNanoseconds(1);
         server.update(timestamp);
         ThreadTools.sleep(timeout.getIntegerValue());
      }
      
      tester.running = false;
      try
      {
         tester.join();
      }
      catch (InterruptedException e)
      {
      }
      jvmStatisticsGenerator.stop();
      server.close();
   }

   private class ThreadTester extends Thread
   {
      private final YoRegistry registry = new YoRegistry("Thread");
      private final YoDouble A = new YoDouble("A", registry);
      private final YoDouble B = new YoDouble("B", registry);
      private final YoDouble C = new YoDouble("C", registry);

      private final YoEnum<TestEnum> echoThreadIn = new YoEnum<>("echoThreadIn", registry, TestEnum.class, false);
      private final YoEnum<TestEnum> echoThreadOut = new YoEnum<>("echoThreadOut", registry, TestEnum.class, false);

      private final DoubleParameter param1 = new DoubleParameter("threadParam1", registry);
      private final DoubleParameter param2 = new DoubleParameter("threadParam2", registry);

      private final YoDouble param1Echo = new YoDouble("threadParam1Echo", registry);
      private final YoDouble param2Echo = new YoDouble("threadParam2Echo", registry);

      public volatile boolean running = true;
      
      public ThreadTester(YoVariableServer server)
      {
         server.addRegistry(registry, null);
         parameterReader.readParametersInRegistry(registry);
      }

      @Override
      public void run()
      {
         while (running)
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

   public static void main(String[] args) throws IOException
   {
      new MemoryLoggerExample();
   }
}
