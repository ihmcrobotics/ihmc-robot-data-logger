package us.ihmc.robotDataLogger.interfaces;

import java.io.IOException;
import java.util.List;

import gnu.trove.map.hash.TObjectIntHashMap;
import us.ihmc.yoVariables.listener.YoVariableChangedListener;
import us.ihmc.yoVariables.variable.YoVariable;

public class VariableChangedProducer
{
   private DataConsumer dataConsumer = null;

   private final TObjectIntHashMap<YoVariable> variableIdentifiers = new TObjectIntHashMap<>();
   private final VariableListener variableListener = new VariableListener();

   public VariableChangedProducer()
   {
   }

   /**
    * Start the variable changed producer listener and add listener to all variableIdentifiers.
    *
    * @param variables List of variables.
    * @throws IOException if the producer cannot be created
    */
   public void startVariableChangedProducers(List<YoVariable> variables, DataConsumer dataConsumer) throws IOException
   {
      this.dataConsumer = dataConsumer;

      for (int i = 0; i < variables.size(); i++)
      {
         variableIdentifiers.put(variables.get(i), i);
         variables.get(i).addListener(variableListener);
      }

   }

   public class VariableListener implements YoVariableChangedListener
   {
      @Override
      public void changed(YoVariable v)
      {
         dataConsumer.writeVariableChangeRequest(variableIdentifiers.get(v), v.getValueAsDouble());
      }
   }
}
