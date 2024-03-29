package us.ihmc.robotDataLogger.handshake;

import static gnu.trove.impl.Constants.DEFAULT_CAPACITY;
import static gnu.trove.impl.Constants.DEFAULT_LOAD_FACTOR;
import static us.ihmc.yoVariables.euclid.referenceFrame.interfaces.FrameIndexMap.NO_ENTRY_KEY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearanceRGBColor;
import us.ihmc.graphicsDescription.color.MutableColor;
import us.ihmc.graphicsDescription.plotting.artifact.Artifact;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsList;
import us.ihmc.graphicsDescription.yoGraphics.plotting.ArtifactList;
import us.ihmc.idl.IDLSequence.Object;
import us.ihmc.idl.serializers.extra.AbstractSerializer;
import us.ihmc.idl.serializers.extra.YAMLSerializer;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.EnumType;
import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.HandshakeFileType;
import us.ihmc.robotDataLogger.HandshakePubSubType;
import us.ihmc.robotDataLogger.JointDefinition;
import us.ihmc.robotDataLogger.ReferenceFrameInformation;
import us.ihmc.robotDataLogger.SCS1YoGraphicObjectMessage;
import us.ihmc.robotDataLogger.SCS2YoGraphicDefinitionMessage;
import us.ihmc.robotDataLogger.YoRegistryDefinition;
import us.ihmc.robotDataLogger.YoType;
import us.ihmc.robotDataLogger.YoVariableDefinition;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition.YoGraphicFieldInfo;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition.YoGraphicFieldsSummary;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.euclid.referenceFrame.interfaces.FrameIndexMap;
import us.ihmc.yoVariables.parameters.BooleanParameter;
import us.ihmc.yoVariables.parameters.DoubleParameter;
import us.ihmc.yoVariables.parameters.EnumParameter;
import us.ihmc.yoVariables.parameters.IntegerParameter;
import us.ihmc.yoVariables.parameters.LongParameter;
import us.ihmc.yoVariables.parameters.ParameterLoadStatus;
import us.ihmc.yoVariables.parameters.SingleParameterReader;
import us.ihmc.yoVariables.parameters.YoParameter;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

/**
 * Class to decode variable data from handshakes
 *
 * @author jesper
 */
public class IDLYoVariableHandshakeParser extends YoVariableHandshakeParser
{
   private final AbstractSerializer<Handshake> serializer;

   private TIntIntHashMap variableOffsets = new TIntIntHashMap();

   public IDLYoVariableHandshakeParser(HandshakeFileType type)
   {
      super();
      switch (type)
      {
         case IDL_YAML:
            serializer = new YAMLSerializer<>(new HandshakePubSubType());
            break;
         default:
            serializer = null;
            break;
      }
   }

   public static int getNumberOfVariables(Handshake handShake)
   {
      int jointStateVariables = 0;
      for (int i = 0; i < handShake.getJoints().size(); i++)
      {
         jointStateVariables += JointState.getNumberOfVariables(handShake.getJoints().get(i).getType());
      }

      return 1 + handShake.getVariables().size() + jointStateVariables;
   }

   @Override
   public void parseFrom(byte[] data) throws IOException
   {
      if (serializer == null)
      {
         throw new RuntimeException();
      }
      Handshake handshake = serializer.deserialize(data);
      parseFrom(handshake);
   }

   @Override
   public void parseFrom(Handshake handshake)
   {
      dt = handshake.getDt();
      List<YoRegistry> regs = parseRegistries(handshake);

      // don't replace those list objects (it's a big code mess), just populate them with received data
      registries.clear();
      registries.addAll(regs);

      List<YoVariable> vars = parseVariables(handshake, regs);

      // don't replace those list objects (it's a big code mess), just populate them with received data
      variables.clear();
      variables.addAll(vars);

      addJointStates(handshake);
      addGraphicObjects(handshake);
      scs2YoGraphics = parseSCS2YoGraphics(handshake);
      frameIndexMap = parseReferenceFrames(handshake);

      numberOfVariables = handshake.getVariables().size();
      numberOfJointStateVariables = getNumberOfJointStateVariables(handshake);
      stateVariables = 1 + numberOfVariables + numberOfJointStateVariables;
   }

   private static List<YoRegistry> parseRegistries(Handshake handshake)
   {
      YoRegistryDefinition rootDefinition = handshake.getRegistries().get(0);
      YoRegistry rootRegistry = new YoRegistry(rootDefinition.getNameAsString());

      List<YoRegistry> registryList = new ArrayList<>();
      registryList.add(rootRegistry);

      for (int i = 1; i < handshake.getRegistries().size(); i++)
      {
         YoRegistryDefinition registryDefinition = handshake.getRegistries().get(i);
         YoRegistry registry = new YoRegistry(registryDefinition.getNameAsString());
         registryList.add(registry);
         registryList.get(registryDefinition.getParent()).addChild(registry);
      }

      return registryList;
   }

   public int getVariableOffset(int registryIndex)
   {
      return variableOffsets.get(registryIndex);
   }

   @SuppressWarnings("rawtypes")
   private List<YoVariable> parseVariables(Handshake handshake, List<YoRegistry> registryList)
   {
      List<YoVariable> variableList = new ArrayList<>();
      for (int i = 0; i < handshake.getVariables().size(); i++)
      {
         YoVariableDefinition yoVariableDefinition = handshake.getVariables().get(i);

         String name = yoVariableDefinition.getNameAsString();
         String description = yoVariableDefinition.getDescriptionAsString();
         int registryIndex = yoVariableDefinition.getRegistry();
         YoRegistry parent = registryList.get(registryIndex);

         double min = yoVariableDefinition.getMin();
         double max = yoVariableDefinition.getMax();

         if (!variableOffsets.contains(registryIndex))
         {
            variableOffsets.put(registryIndex, i);
         }

         YoType type = yoVariableDefinition.getType();
         if (yoVariableDefinition.getIsParameter())
         {
            YoParameter newParameter;
            switch (type)
            {
               case DoubleYoVariable:
                  newParameter = new DoubleParameter(name, description, parent, min, max);
                  break;

               case IntegerYoVariable:
                  newParameter = new IntegerParameter(name, description, parent, (int) min, (int) max);
                  break;

               case BooleanYoVariable:
                  newParameter = new BooleanParameter(name, description, parent);
                  break;

               case LongYoVariable:
                  newParameter = new LongParameter(name, description, parent, (long) min, (long) max);
                  break;

               case EnumYoVariable:
                  EnumType enumType = handshake.getEnumTypes().get(yoVariableDefinition.getEnumType());
                  String[] names = enumType.getEnumValues().toStringArray();
                  boolean allowNullValues = yoVariableDefinition.getAllowNullValues();
                  newParameter = new EnumParameter<>(name, description, parent, allowNullValues, names);
                  break;

               default:
                  throw new RuntimeException("Unknown YoVariable type: " + type.name());
            }

            //This is the case for some logs. A special enum may need to be used here. I'm not sure these matter at all for a log?
            if (yoVariableDefinition.getLoadStatus() == null)
            {
               SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.LOADED);
            }
            else
            {
               switch (yoVariableDefinition.getLoadStatus())
               {
                  case Unloaded:
                     SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.UNLOADED);
                     break;
                  case Default:
                     SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.DEFAULT);
                     break;
                  case Loaded:
                     SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.LOADED);
                     break;
                  default:
                     throw new RuntimeException("Unknown load status: " + yoVariableDefinition.getLoadStatus());
               }

            }
            YoVariable newVariable = parent.getVariable(parent.getNumberOfVariables() - 1);

            // Test if this is the correct variable
            if (newParameter != newVariable.getParameter())
            {
               throw new RuntimeException("Last variable index in the registry is not the parameter just added.");
            }
            variableList.add(newVariable);

         }
         else
         {
            YoVariable newVariable;
            switch (type)
            {
               case DoubleYoVariable:
                  newVariable = new YoDouble(name, description, parent);
                  break;

               case IntegerYoVariable:
                  newVariable = new YoInteger(name, description, parent);
                  break;

               case BooleanYoVariable:
                  newVariable = new YoBoolean(name, description, parent);
                  break;

               case LongYoVariable:
                  newVariable = new YoLong(name, description, parent);
                  break;

               case EnumYoVariable:
                  EnumType enumType = handshake.getEnumTypes().get(yoVariableDefinition.getEnumType());
                  String[] names = enumType.getEnumValues().toStringArray();
                  boolean allowNullValues = yoVariableDefinition.getAllowNullValues();
                  newVariable = new YoEnum(name, description, parent, allowNullValues, names);
                  break;

               default:
                  throw new RuntimeException("Unknown YoVariable type: " + type.name());
            }
            newVariable.setVariableBounds(min, max);
            variableList.add(newVariable);
         }
      }

      return variableList;
   }

   private int getNumberOfJointStateVariables(Handshake handshake)
   {
      int numberOfJointStates = 0;
      for (int i = 0; i < handshake.getJoints().size(); i++)
      {
         JointDefinition joint = handshake.getJoints().get(i);
         numberOfJointStates += JointState.getNumberOfVariables(joint.getType());
      }
      return numberOfJointStates;
   }

   private void addJointStates(Handshake handshake)
   {
      for (int i = 0; i < handshake.getJoints().size(); i++)
      {
         JointDefinition joint = handshake.getJoints().get(i);
         jointStates.add(JointState.createJointState(joint.getNameAsString(), joint.getType()));
      }
   }

   private void addGraphicObjects(Handshake yoProtoHandshake)
   {
      HashMap<String, YoGraphicsList> dgoListMap = new HashMap<>();
      String listName;
      YoGraphicsList dgoList;
      for (int i = 0; i < yoProtoHandshake.getGraphicObjects().size(); i++)
      {
         listName = "default";
         if (!yoProtoHandshake.getGraphicObjects().get(i).getListNameAsString().isEmpty())
         {
            listName = yoProtoHandshake.getGraphicObjects().get(i).getListNameAsString();
         }

         if (dgoListMap.containsKey(listName))
         {
            dgoList = dgoListMap.get(listName);
         }
         else
         {
            dgoList = new YoGraphicsList(listName);
            dgoListMap.put(listName, dgoList);
         }

         try
         {
            dgoList.add((YoGraphic) getRemoteGraphic(yoProtoHandshake.getGraphicObjects().get(i)));
         }
         catch (Exception e)
         {
            LogTools.error("Got exception: " + e.getClass().getSimpleName() + " when loading a YoGraphic: " + e.getMessage());
         }
      }

      for (String list : dgoListMap.keySet())
      {
         scs1YoGraphics.registerYoGraphicsList(dgoListMap.get(list));
      }

      ArtifactList artifactList = new ArtifactList("remote");
      for (int i = 0; i < yoProtoHandshake.getArtifacts().size(); i++)
      {
         try
         {
            artifactList.add((Artifact) getRemoteGraphic(yoProtoHandshake.getArtifacts().get(i)));
         }
         catch (Exception e)
         {
            LogTools.error("Got exception: " + e.getClass().getSimpleName() + " when loading a Artifact: " + e.getMessage());
         }
      }
      scs1YoGraphics.registerArtifactList(artifactList);
   }

   private RemoteYoGraphic getRemoteGraphic(SCS1YoGraphicObjectMessage graphicObjectMessage)
   {
      int registrationID = graphicObjectMessage.getRegistrationID();

      String name = graphicObjectMessage.getNameAsString();
      YoVariable[] vars = new YoVariable[graphicObjectMessage.getYoVariableIndex().size()];
      for (int v = 0; v < vars.length; v++)
         vars[v] = variables.get(graphicObjectMessage.getYoVariableIndex().get(v));

      double[] consts = graphicObjectMessage.getConstants().toArray();

      AppearanceDefinition appearance = new YoAppearanceRGBColor(new MutableColor((float) graphicObjectMessage.getAppearance().getR(),
                                                                                  (float) graphicObjectMessage.getAppearance().getG(),
                                                                                  (float) graphicObjectMessage.getAppearance().getB()),
                                                                 graphicObjectMessage.getAppearance().getTransparency());

      return yoGraphicFromMessage(registrationID, name, vars, consts, appearance);
   }

   private static List<YoGraphicGroupDefinition> parseSCS2YoGraphics(Handshake handshake)
   {
      List<YoGraphicFieldsSummary> yoGraphicFieldsSummaryList = new ArrayList<>();
      Object<SCS2YoGraphicDefinitionMessage> msgList = handshake.getScs2YoGraphicDefinitions();

      for (int i = 0; i < msgList.size(); i++)
      {
         SCS2YoGraphicDefinitionMessage msg = msgList.get(i);
         int fields = msg.getFieldNames().size();
         YoGraphicFieldsSummary summary = new YoGraphicFieldsSummary();
         for (int j = 0; j < fields; j++)
         {
            summary.add(new YoGraphicFieldInfo(msg.getFieldNames().get(j).toString(), msg.getFieldValues().get(j).toString()));
         }
         yoGraphicFieldsSummaryList.add(summary);
      }

      return YoGraphicDefinition.parseTreeYoGraphicFieldsSummary(yoGraphicFieldsSummaryList);
   }

   private static FrameIndexMap parseReferenceFrames(Handshake handshake)
   {
      ReferenceFrameInformation referenceFrameInformation = handshake.getReferenceFrameInformation();
      TObjectLongMap<ReferenceFrame> frameToIndex = new TObjectLongHashMap<>(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, NO_ENTRY_KEY);
      TLongObjectMap<ReferenceFrame> indexToframe = new TLongObjectHashMap<>(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, NO_ENTRY_KEY);
      for (int i = 0; i < referenceFrameInformation.getFrameNames().size(); i++)
      {
         // TODO: one day we can actually fix this frame tree to match the controller, back the transforms by yo variables and have rviz kinda.
         RigidBodyTransform transform = new RigidBodyTransform();

         String name = referenceFrameInformation.getFrameNames().get(i).toString();
         ReferenceFrame frame = ReferenceFrameTools.constructFrameWithUnchangingTransformToParent(name, ReferenceFrame.getWorldFrame(), transform);
         long index = referenceFrameInformation.getFrameIndices().get(i);
         frameToIndex.put(frame, index);
         indexToframe.put(index, frame);
      }

      return new FrameIndexMap()
      {
         @Override
         public void put(ReferenceFrame referenceFrame)
         {
            throw new UnsupportedOperationException();
         }

         @Override
         public ReferenceFrame getReferenceFrame(long frameIndex)
         {
            return indexToframe.get(frameIndex);
         }

         @Override
         public long getFrameIndex(ReferenceFrame referenceFrame)
         {
            return frameToIndex.get(referenceFrame);
         }
      };
   }
}
