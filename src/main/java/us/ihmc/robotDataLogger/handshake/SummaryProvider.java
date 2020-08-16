package us.ihmc.robotDataLogger.handshake;

import java.util.ArrayList;

import us.ihmc.yoVariables.variable.YoVariable;

public class SummaryProvider
{
   private boolean summarize = false;
   private String summaryTriggerVariable;
   private ArrayList<String> summarizedVariables = new ArrayList<>();
   private ArrayList<YoVariable> summarizedYoVariables = new ArrayList<>();

   public void addSummarizedVariable(YoVariable summarizedYoVariable)
   {
      summarizedYoVariables.add(summarizedYoVariable);
   }

   public boolean isSummarize()
   {
      return summarize;
   }

   public void setSummarize(boolean summarize)
   {
      this.summarize = summarize;
   }

   public String getSummaryTriggerVariable()
   {
      return summaryTriggerVariable;
   }

   public void setSummaryTriggerVariable(String summaryTriggerVariable)
   {
      this.summaryTriggerVariable = summaryTriggerVariable;
   }

   public String[] getSummarizedVariables()
   {
      ArrayList<String> allVariables = new ArrayList<>();
      allVariables.addAll(summarizedVariables);
      for (YoVariable var : summarizedYoVariables)
      {
         allVariables.add(var.getFullNameString());
      }

      return allVariables.toArray(new String[allVariables.size()]);
   }

   public void addSummarizedVariable(String summarizedVariable)
   {
      summarizedVariables.add(summarizedVariable);
   }

}
