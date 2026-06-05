package us.ihmc.robotDataLogger.handshake;

import gnu.trove.map.hash.TObjectIntHashMap;
import logger_msgs.EnumType;
import logger_msgs.Handshake;
import logger_msgs.JointDefinition;
import logger_msgs.LoadStatus;
import logger_msgs.ReferenceFrameInformation;
import logger_msgs.SCS2YoGraphicDefinitionMessage;
import logger_msgs.YoRegistryDefinition;
import logger_msgs.MessageTypes;
import logger_msgs.YoType;
import logger_msgs.YoVariableDefinition;
import org.apache.commons.lang3.tuple.ImmutablePair;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.dataBuffers.RegistrySendBufferBuilder;
import us.ihmc.robotDataLogger.jointState.JointHolder;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition.YoGraphicFieldInfo;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition.YoGraphicFieldsSummary;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.parameters.ParameterLoadStatus;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class YoVariableHandShakeBuilder
{
   private final Handshake handshake = new Handshake();
   private final ArrayList<JointHolder> jointHolders = new ArrayList<>();
   private final TObjectIntHashMap<YoVariable> yoVariableIndices = new TObjectIntHashMap<>();
   private final ArrayList<ImmutablePair<YoVariable, YoRegistry>> variablesAndRootRegistries = new ArrayList<>();
   private final TObjectIntHashMap<String> enumDescriptions = new TObjectIntHashMap<>();

   private int registryID = 1;
   private int enumID = 0;

   public YoVariableHandShakeBuilder(String rootRegistryName, double dt)
   {
      createRootRegistry(rootRegistryName);

      handshake.setDt(dt);

   }

   private void addSCS2YoGraphicDefinition(YoGraphicGroupDefinition rootDefinition)
   {
      if (rootDefinition == null)
         return;

      List<YoGraphicFieldsSummary> treeFieldValueInfo = YoGraphicDefinition.exportSubtreeYoGraphicFieldsSummaryList(rootDefinition);

      for (YoGraphicFieldsSummary yoGraphicFieldsValuesInfo : treeFieldValueInfo)
      {
         SCS2YoGraphicDefinitionMessage msg = handshake.getScs2YoGraphicDefinitions().add();
         for (YoGraphicFieldInfo entry : yoGraphicFieldsValuesInfo)
         {
            msg.getFieldNames().add().append(entry.getFieldName());
            msg.getFieldValues().add().append(entry.getFieldValue());
         }
      }
   }

   private void addJointHolders(List<JointHolder> jointHolders)
   {
      for (JointHolder jointHolder : jointHolders)
      {
         JointDefinition jointDefinition = handshake.getJoints().add();
         jointDefinition.setName(jointHolder.getName());
         jointDefinition.setType(jointHolder.getJointType().getType());

         this.jointHolders.add(jointHolder);
      }
   }

   public List<JointHolder> getJointHolders()
   {
      return Collections.unmodifiableList(jointHolders);
   }

   public int getNumberOfJointStates()
   {
      return RegistrySendBufferBuilder.getNumberOfJointStates(jointHolders);
   }

   private void createRootRegistry(String rootRegistryName)
   {
      YoRegistryDefinition yoRegistryDescription = handshake.getRegistries().add();
      yoRegistryDescription.setName(rootRegistryName);
      yoRegistryDescription.setParent((short) 0);
   }

   public void addRegistryBuffer(RegistrySendBufferBuilder builder)
   {
      YoRegistry registry = builder.getYoRegistry();

      int registryID = addRegistry(0, registry, builder.getVariables(), registry);
      addSCS2YoGraphicDefinition(builder.getSCS2YoGraphics());

      builder.build(registryID);
      List<JointHolder> jointHolders = builder.getJointHolders();
      if (jointHolders != null && !jointHolders.isEmpty())
      {
         if (!this.jointHolders.isEmpty())
         {
            throw new RuntimeException("Cannot register multiple registries with joint holders");
         }

         addJointHolders(jointHolders);
      }
   }

   private int addRegistry(int parentID, YoRegistry registry, List<YoVariable> variableListToPack, YoRegistry rootRegistry)
   {
      int myID = registryID;
      if (myID > handshake.getRegistries().capacity())
      {
         throw new RuntimeException("The number of registries exceeds the maximum number of registries for the logger (" + handshake.getRegistries().capacity()
               + ")");
      }
      registryID++;

      YoRegistryDefinition yoRegistryDescription = handshake.getRegistries().add();
      yoRegistryDescription.setName(registry.getName());
      yoRegistryDescription.setParent((short) parentID);

      addVariables(myID, registry, variableListToPack, rootRegistry);

      for (YoRegistry child : registry.getChildren())
      {
         addRegistry(myID, child, variableListToPack, rootRegistry);
      }

      return myID;
   }

   private short getOrAddEnumType(String enumClass, String[] enumTypes)
   {
      if (enumDescriptions.containsKey(enumClass))
      {
         return (short) enumDescriptions.get(enumClass);
      }

      int myID = enumID;
      if (myID > handshake.getEnumTypes().capacity())
      {
         throw new RuntimeException("The number of enum types exceeds the maximum number of enum types for the logger (" + handshake.getEnumTypes().capacity()
               + ")");
      }
      enumID++;

      EnumType enumTypeDescription = handshake.getEnumTypes().add();
      String name = enumClass;
      if (name.length() > 255)
      {
         name = name.substring(name.length() - 255);
         if (name.startsWith("."))
         {
            name = name.substring(1);
         }
      }

      enumTypeDescription.setName(name);
      enumTypeDescription.getEnumValues().ensureMinCapacity(enumTypes.length);
      handshake.getEnumTypes().ensureMinCapacity(enumTypes.length);
      if (enumTypes.length > enumTypeDescription.getEnumValues().capacity())
      {
         throw new RuntimeException("The number of enum values for " + name + " exceeds the maximum number of enum values ("
               + enumTypeDescription.getEnumValues().capacity() + ")");
      }
      for (String enumType : enumTypes)
      {
         if (enumType == null)
         {
            enumType = "null";
         }
         if (enumType.length() > 255)
         {
            enumType = enumType.substring(0, 255);
         }

         enumTypeDescription.getEnumValues().add(enumType);
      }

      enumDescriptions.put(enumClass, myID);

      return (short) myID;
   }

   private void addVariables(int registryID, YoRegistry registry, List<YoVariable> variableListToPack, YoRegistry rootRegistry)
   {
      List<YoVariable> variables = registry.getVariables();
      handshake.getVariables().ensureMinCapacity(variables.size());
      if (variables.size() > handshake.getVariables().capacity())
      {
         throw new RuntimeException("The number of variables exceeds the maximum number of variables for the logger (" + handshake.getVariables().capacity()
               + ")");
      }
      for (YoVariable variable : variables)
      {
         YoVariableDefinition yoVariableDefinition = handshake.getVariables().add();
         yoVariableDefinition.setName(variable.getName());

         String description = variable.getDescription();
         if (description != null && description.length() > 255)
         {
            description = description.substring(0, 255);
         }
         yoVariableDefinition.setDescription(description);
         yoVariableDefinition.setRegistry((short) registryID);
         yoVariableDefinition.setIsParameter(variable.isParameter());
         yoVariableDefinition.setMin(variable.getLowerBound());
         yoVariableDefinition.setMax(variable.getUpperBound());
         if (variable.isParameter())
         {
            ParameterLoadStatus loadStatus = variable.getParameter().getLoadStatus();
            switch (loadStatus)
            {
               case UNLOADED:
                  yoVariableDefinition.setLoadStatus(LoadStatus.UNLOADED);
                  break;
               case DEFAULT:
                  yoVariableDefinition.setLoadStatus(LoadStatus.DEFAULT);
                  break;
               case LOADED:
                  yoVariableDefinition.setLoadStatus(LoadStatus.LOADED);
                  break;
               default:
                  throw new RuntimeException("Unknown load status: " + loadStatus);
            }
         }
         else
         {
            yoVariableDefinition.setLoadStatus(LoadStatus.NOPARAMETER);
         }

         switch (variable.getType())
         {
            case DOUBLE:
               yoVariableDefinition.getType().set(MessageTypes.yoType(YoType.DOUBLEYOVARIABLE));
               break;
            case INTEGER:
               yoVariableDefinition.getType().set(MessageTypes.yoType(YoType.INTEGERYOVARIABLE));
               break;
            case BOOLEAN:
               yoVariableDefinition.getType().set(MessageTypes.yoType(YoType.BOOLEANYOVARIABLE));
               break;
            case LONG:
               yoVariableDefinition.getType().set(MessageTypes.yoType(YoType.LONGYOVARIABLE));
               break;
            case ENUM:
               yoVariableDefinition.getType().set(MessageTypes.yoType(YoType.ENUMYOVARIABLE));
               if (((YoEnum<?>) variable).isBackedByEnum())
               {
                  yoVariableDefinition.setEnumType(getOrAddEnumType(((YoEnum<?>) variable).getEnumType().getCanonicalName(),
                                                                    ((YoEnum<?>) variable).getEnumValuesAsString()));
               }
               else
               {
                  yoVariableDefinition.setEnumType(getOrAddEnumType(variable.getFullNameString() + ".EnumType",
                                                                    ((YoEnum<?>) variable).getEnumValuesAsString()));
               }
               yoVariableDefinition.setAllowNullValues(((YoEnum<?>) variable).isNullAllowed());
               break;
            default:
               throw new RuntimeException("Unknown variable type: " + variable.getType());
         }

         variableListToPack.add(variable);
         variablesAndRootRegistries.add(new ImmutablePair<YoVariable, YoRegistry>(variable, rootRegistry));
         yoVariableIndices.put(variable, variablesAndRootRegistries.size() - 1);

      }
   }

   public int getNumberOfVariables()
   {
      return variablesAndRootRegistries.size();
   }

   public ArrayList<ImmutablePair<YoVariable, YoRegistry>> getVariablesAndRootRegistries()
   {
      return variablesAndRootRegistries;
   }

   public Handshake getHandShake()
   {
      return handshake;
   }

   public void setSummaryProvider(SummaryProvider summaryProvider)
   {
      handshake.getSummary().setCreateSummary(summaryProvider.isSummarize());
      if (summaryProvider.isSummarize())
      {
         handshake.getSummary().setSummaryTriggerVariable(summaryProvider.getSummaryTriggerVariable());
         String[] summarizedVariables = summaryProvider.getSummarizedVariables();
         for (int i = 0; i < summarizedVariables.length; i++)
         {
            String var = summarizedVariables[i];
            handshake.getSummary().getSummarizedVariables().add(var);
         }
      }
   }

   public void setFrames(ReferenceFrame rootFrame)
   {
      try
      {
         Collection<ReferenceFrame> frames = ReferenceFrameTools.getAllFramesInTree(rootFrame);
         ReferenceFrameInformation referenceFrameInformation = handshake.getReferenceFrameInformation();
         int i = 0;
         int packetSizeLimit = 8192;
         int frameCount = Math.min(frames.size(), packetSizeLimit);

         // Clear and ensure capacity - following the pattern from CustomLogDataPublisherType
         referenceFrameInformation.getFrameIndices().clear();
         referenceFrameInformation.getFrameIndices().ensureMinCapacity(frameCount);

         for (Iterator<ReferenceFrame> iterator = frames.iterator(); iterator.hasNext() && i < packetSizeLimit; i++)
         {
            ReferenceFrame frame = iterator.next();
            referenceFrameInformation.getFrameNames().add(frame.getName());

            referenceFrameInformation.getFrameIndices().add((int) frame.getFrameIndex());
         }

         if (frames.size() > packetSizeLimit)
         {
            LogTools.warn("There are too many frames to pack! ({}) We chopped off the end.", frames.size());
         }
      }
      catch (NullPointerException nullPointerException)
      {
         LogTools.error("Setting frames failed, most likely due to a threading violation. Frames may not be set.");
      }
   }
}