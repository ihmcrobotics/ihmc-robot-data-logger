package us.ihmc.robotDataLogger.handshake;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import logger_msgs.msg.dds.EnumType;
import logger_msgs.msg.dds.Handshake;
import logger_msgs.msg.dds.HandshakeFileType;
import logger_msgs.msg.dds.JointDefinition;
import logger_msgs.msg.dds.LoadStatus;
import logger_msgs.msg.dds.ReferenceFrameInformation;
import logger_msgs.msg.dds.SCS2YoGraphicDefinitionMessage;
import logger_msgs.msg.dds.YoRegistryDefinition;
import logger_msgs.msg.dds.YoType;
import logger_msgs.msg.dds.YoVariableDefinition;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.idl.serializers.extra.ROS2YAMLSerializer;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static gnu.trove.impl.Constants.DEFAULT_CAPACITY;
import static gnu.trove.impl.Constants.DEFAULT_LOAD_FACTOR;
import static us.ihmc.yoVariables.euclid.referenceFrame.interfaces.FrameIndexMap.NO_ENTRY_KEY;

/**
 * Class to decode variable data from handshakes
 *
 * @author jesper
 */
public class IDLYoVariableHandshakeParser extends YoVariableHandshakeParser
{
   private final ROS2YAMLSerializer<Handshake> serializer;

   private TIntIntHashMap variableOffsets = new TIntIntHashMap();

   public IDLYoVariableHandshakeParser(HandshakeFileType type)
   {
      super();
      switch (type.getType())
      {
         case HandshakeFileType.IDL_YAML:
            serializer = new ROS2YAMLSerializer<>(Handshake.class);
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
         byte jointType = handShake.getJoints().get(i).getType();
         jointStateVariables += JointState.getNumberOfVariables(jointType);
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

         byte type = yoVariableDefinition.getType().getType();
         if (yoVariableDefinition.getIsParameter())
         {
            YoParameter newParameter;
            switch (type)
            {
               case YoType.DOUBLEYOVARIABLE:
                  newParameter = new DoubleParameter(name, description, parent, min, max);
                  break;

               case YoType.INTEGERYOVARIABLE:
                  newParameter = new IntegerParameter(name, description, parent, (int) min, (int) max);
                  break;

               case YoType.BOOLEANYOVARIABLE:
                  newParameter = new BooleanParameter(name, description, parent);
                  break;

               case YoType.LONGYOVARIABLE:
                  newParameter = new LongParameter(name, description, parent, (long) min, (long) max);
                  break;

               case YoType.ENUMYOVARIABLE:
                  EnumType enumType = handshake.getEnumTypes().get(yoVariableDefinition.getEnumType());
                  String[] names = enumType.getEnumValues().toStringArray();
                  boolean allowNullValues = yoVariableDefinition.getAllowNullValues();
                  newParameter = new EnumParameter<>(name, description, parent, allowNullValues, names);
                  break;

               default:
                  throw new RuntimeException("Unknown YoVariable type: " + type);
            }


            switch (yoVariableDefinition.getLoadStatus())
            {
               case LoadStatus.UNLOADED:
                  SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.UNLOADED);
                  break;
               case LoadStatus.DEFAULT:
                  SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.DEFAULT);
                  break;
               case LoadStatus.LOADED:
                  SingleParameterReader.readParameter(newParameter, 0.0, ParameterLoadStatus.LOADED);
                  break;
               default:
                  throw new RuntimeException("Unknown load status: " + yoVariableDefinition.getLoadStatus());
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
               case YoType.DOUBLEYOVARIABLE:
                  newVariable = new YoDouble(name, description, parent);
                  break;

               case YoType.INTEGERYOVARIABLE:
                  newVariable = new YoInteger(name, description, parent);
                  break;

               case YoType.BOOLEANYOVARIABLE:
                  newVariable = new YoBoolean(name, description, parent);
                  break;

               case YoType.LONGYOVARIABLE:
                  newVariable = new YoLong(name, description, parent);
                  break;

               case YoType.ENUMYOVARIABLE:
                  EnumType enumType = handshake.getEnumTypes().get(yoVariableDefinition.getEnumType());
                  String[] names = enumType.getEnumValues().toStringArray();
                  boolean allowNullValues = yoVariableDefinition.getAllowNullValues();
                  newVariable = new YoEnum(name, description, parent, allowNullValues, names);
                  break;

               default:
                  throw new RuntimeException("Unknown YoVariable type: " + type);
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
         byte jointType = joint.getType();
         numberOfJointStates += JointState.getNumberOfVariables(jointType);
      }
      return numberOfJointStates;
   }

   private void addJointStates(Handshake handshake)
   {
      for (int i = 0; i < handshake.getJoints().size(); i++)
      {
         JointDefinition joint = handshake.getJoints().get(i);
         byte jointType = joint.getType();
         jointStates.add(JointState.createJointState(joint.getNameAsString(), jointType));
      }
   }

   private static List<YoGraphicGroupDefinition> parseSCS2YoGraphics(Handshake handshake)
   {
      List<YoGraphicFieldsSummary> yoGraphicFieldsSummaryList = new ArrayList<>();
      IDLObjectSequence<SCS2YoGraphicDefinitionMessage> msgList = handshake.getScs2YoGraphicDefinitions();

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
         long index = referenceFrameInformation.getFrameIndices().getBuffer().get(i);
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
