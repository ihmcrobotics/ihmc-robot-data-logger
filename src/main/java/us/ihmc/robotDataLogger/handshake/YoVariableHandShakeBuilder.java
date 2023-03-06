package us.ihmc.robotDataLogger.handshake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;

import gnu.trove.map.hash.TObjectIntHashMap;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.graphicsDescription.plotting.artifact.Artifact;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphicFactory;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsList;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.ArtifactList;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.EnumType;
import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.JointDefinition;
import us.ihmc.robotDataLogger.LoadStatus;
import us.ihmc.robotDataLogger.ReferenceFrameInformation;
import us.ihmc.robotDataLogger.SCS1AppearanceDefinitionMessage;
import us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage;
import us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessage;
import us.ihmc.robotDataLogger.YoRegistryDefinition;
import us.ihmc.robotDataLogger.YoType;
import us.ihmc.robotDataLogger.YoVariableDefinition;
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

public class YoVariableHandShakeBuilder
{
   private final RemoteYoGraphicFactory yoGraphicFactory = new RemoteYoGraphicFactory();
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

   private void addSCS1YoGraphicObjects(YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      if (yoGraphicsListRegistry == null)
         return;

      ArrayList<YoGraphicsList> yoGraphicsLists = new ArrayList<>();
      yoGraphicsListRegistry.getRegisteredYoGraphicsLists(yoGraphicsLists);
      for (YoGraphicsList yoGraphicsList : yoGraphicsLists)
      {
         for (YoGraphic yoGraphic : yoGraphicsList.getYoGraphics())
         {
            if (yoGraphic instanceof RemoteYoGraphic)
            {
               if (handshake.getGraphicObjects().remaining() == 0)
               {
                  throw new RuntimeException("The number of YoGraphics exceeds the maximum amount for the logger (" + handshake.getGraphicObjects().capacity()
                        + ")");
               }

               if (verifyDynamicGraphicObject((RemoteYoGraphic) yoGraphic))
               {
                  SCS1YoGraphicObjectMessage msg = handshake.getGraphicObjects().add();
                  msg.setListName(yoGraphicsList.getLabel());
                  messageFromDynamicGraphicObject((RemoteYoGraphic) yoGraphic, msg);
               }

            }
            else
            {
               System.err.println("Remote DGO not supported:  " + yoGraphic.getClass().getSimpleName());
            }
         }
      }

      ArrayList<ArtifactList> artifactLists = new ArrayList<>();
      yoGraphicsListRegistry.getRegisteredArtifactLists(artifactLists);

      for (ArtifactList artifactList : artifactLists)
      {
         for (Artifact artifact : artifactList.getArtifacts())
         {

            if (artifact instanceof RemoteYoGraphic)
            {
               if (handshake.getArtifacts().remaining() == 0)
               {
                  throw new RuntimeException("The number of Artifacts exceeds the maximum amount for the logger (" + handshake.getArtifacts().capacity() + ")");
               }

               if (verifyDynamicGraphicObject((RemoteYoGraphic) artifact))
               {
                  SCS1YoGraphicObjectMessage msg = handshake.getArtifacts().add();
                  messageFromDynamicGraphicObject((RemoteYoGraphic) artifact, msg);
               }
            }
            else
            {
               System.err.println("Remote artifact not supported: " + artifact.getClass().getSimpleName());
            }
         }
      }
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
         jointDefinition.setType(jointHolder.getJointType());

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
      addSCS1YoGraphicObjects(builder.getSCS1YoGraphics());
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
                  yoVariableDefinition.setLoadStatus(LoadStatus.Unloaded);
                  break;
               case DEFAULT:
                  yoVariableDefinition.setLoadStatus(LoadStatus.Default);
                  break;
               case LOADED:
                  yoVariableDefinition.setLoadStatus(LoadStatus.Loaded);
                  break;
               default:
                  throw new RuntimeException("Unknown load status: " + loadStatus);
            }
         }
         else
         {
            yoVariableDefinition.setLoadStatus(LoadStatus.NoParameter);
         }

         switch (variable.getType())
         {
            case DOUBLE:
               yoVariableDefinition.setType(YoType.DoubleYoVariable);
               break;
            case INTEGER:
               yoVariableDefinition.setType(YoType.IntegerYoVariable);
               break;
            case BOOLEAN:
               yoVariableDefinition.setType(YoType.BooleanYoVariable);
               break;
            case LONG:
               yoVariableDefinition.setType(YoType.LongYoVariable);
               break;
            case ENUM:
               yoVariableDefinition.setType(YoType.EnumYoVariable);
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

   private boolean verifyDynamicGraphicObject(RemoteYoGraphic remoteYoGraphic)
   {
      for (YoVariable yoVariable : remoteYoGraphic.getVariables())
      {
         if (!yoVariableIndices.containsKey(yoVariable))
         {
            LogTools.error("Backing YoRegistry not added for {}, variable: {}. Disabling visualizer for {}.",
                           remoteYoGraphic.getName(),
                           yoVariable,
                           remoteYoGraphic.getName());
            return false;
         }
      }

      return true;
   }

   private void messageFromDynamicGraphicObject(RemoteYoGraphic obj, SCS1YoGraphicObjectMessage objectMessage)
   {
      objectMessage.setRegistrationID(yoGraphicFactory.getRegistrationID(obj.getClass()));
      objectMessage.setName(obj.getName());

      try
      {
         SCS1AppearanceDefinitionMessage appearanceMessage = objectMessage.getAppearance();
         appearanceMessage.setR(obj.getAppearance().getColor().getX());
         appearanceMessage.setG(obj.getAppearance().getColor().getY());
         appearanceMessage.setB(obj.getAppearance().getColor().getZ());
         appearanceMessage.setTransparency(obj.getAppearance().getTransparency());
      }
      catch (NotImplementedException e)
      {
         System.err.println(e.getMessage());
      }

      if (obj.getVariables().length > objectMessage.getYoVariableIndex().capacity())
      {
         throw new RuntimeException(obj.getName() + " has too many variables. It has " + obj.getVariables().length + " variables");
      }

      for (YoVariable yoVar : obj.getVariables())
      {
         if (!yoVariableIndices.containsKey(yoVar))
         {
            throw new RuntimeException("Backing YoRegistry not added for " + obj.getName() + ", variable: " + yoVar);
         }
         int index = yoVariableIndices.get(yoVar);
         objectMessage.getYoVariableIndex().add((short) index);
      }

      for (double d : obj.getConstants())
      {
         objectMessage.getConstants().add(d);
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
         for (Iterator<ReferenceFrame> iterator = frames.iterator(); iterator.hasNext() && i++ < packetSizeLimit;)
         {
            ReferenceFrame frame = iterator.next();
            referenceFrameInformation.getFrameNames().add(frame.getName());
            referenceFrameInformation.getFrameIndices().add(frame.getFrameIndex());
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