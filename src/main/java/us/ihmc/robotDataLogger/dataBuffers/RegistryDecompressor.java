package us.ihmc.robotDataLogger.dataBuffers;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.List;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.interfaces.VariableChangedProducer;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;
import us.ihmc.yoVariables.listener.YoVariableChangedListener;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistryDecompressor
{
   private final List<YoVariable> variables;
   private final List<JointState> jointStates;

   private final ByteBuffer decompressBuffer;
   private final CompressionImplementation compressionImplementation;

   public RegistryDecompressor(List<YoVariable> variables, List<JointState> jointStates)
   {
      this.variables = variables;
      this.jointStates = jointStates;
      this.decompressBuffer = ByteBuffer.allocate(variables.size() * 8);

      this.compressionImplementation = CompressionImplementationFactory.instance();

   }

   private void setAndNotify(YoVariable variable, long newValue)
   {
      long previousValue = variable.getValueAsLongBits();
      variable.setValueFromLongBits(newValue, false);
      if (previousValue != newValue)
      {
         List<YoVariableChangedListener> changedListeners = variable.getListeners();
         if (changedListeners != null)
         {
            for (int listener = 0; listener < changedListeners.size(); listener++)
            {
               YoVariableChangedListener changedListener = changedListeners.get(listener);
               if (!(changedListener instanceof VariableChangedProducer.VariableListener))
               {
                  changedListener.changed(variable);
               }
            }
         }
      }
   }

   public void decompressSegment(RegistryReceiveBuffer buffer, int registryOffset)
   {
      decompressBuffer.clear();
      try
      {
         compressionImplementation.decompress(buffer.getData(), decompressBuffer, buffer.getNumberOfVariables() * 8);
      }
      catch (Throwable e)
      {
         // Malformed packet. Just skip.
         LogTools.error("Cannot decompress incoming packet. Skipping packet. " + e.getMessage());
         return;
      }
      decompressBuffer.flip();
      LongBuffer longData = decompressBuffer.asLongBuffer();

      // Sanity check
      if (longData.remaining() != buffer.getNumberOfVariables())
      {
         System.err.println("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }
      int numberOfVariables = buffer.getNumberOfVariables();

      int offset = registryOffset;
      for (int i = 0; i < numberOfVariables; i++)
      {
         setAndNotify(variables.get(i + offset), longData.get());
      }

      double[] jointStateArray = buffer.getJointStates();
      if (jointStateArray.length > 0)
      {
         DoubleBuffer jointStateBuffer = DoubleBuffer.wrap(jointStateArray);
         for (int i = 0; i < jointStates.size(); i++)
         {
            jointStates.get(i).update(jointStateBuffer);
         }
      }
   }
}
