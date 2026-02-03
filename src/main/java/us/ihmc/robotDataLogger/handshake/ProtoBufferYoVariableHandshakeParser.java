package us.ihmc.robotDataLogger.handshake;

import com.google.protobuf.InvalidProtocolBufferException;
import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.JointType;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake.JointDefinition;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake.YoRegistryDefinition;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake.YoVariableDefinition;
import us.ihmc.robotDataLogger.jointState.JointState;
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

/**
 * Deprecated class to support legacy log files that still contain a description based on a
 * protobuffer handshake
 *
 * @author jesper
 */
@Deprecated
public class ProtoBufferYoVariableHandshakeParser extends YoVariableHandshakeParser
{
   ProtoBufferYoVariableHandshakeParser()
   {
      super();
   }

   private static JointType convertJointType(us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake.JointDefinition.JointType type)
   {
      switch (type)
      {
         case OneDoFJoint:
            return JointType.OneDoFJoint;
         case SiXDoFJoint:
            return JointType.SiXDoFJoint;
         default:
            throw new RuntimeException();
      }
   }

   private static YoProtoHandshake parseYoProtoHandshake(byte[] handShake)
   {
      try
      {
         return YoProtoHandshake.parseFrom(handShake);
      }
      catch (InvalidProtocolBufferException e)
      {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void parseFrom(byte[] handShake)
   {
      YoProtoHandshake yoProtoHandshake = parseYoProtoHandshake(handShake);

      dt = yoProtoHandshake.getDt();
      List<YoRegistry> regs = parseRegistries(yoProtoHandshake);

      // don't replace those list objects (it's a big code mess), just populate them with received data
      registries.clear();
      registries.addAll(regs);

      List<YoVariable> vars = parseVariables(yoProtoHandshake, regs);

      // don't replace those list objects (it's a big code mess), just populate them with received data
      variables.clear();
      variables.addAll(vars);

      addJointStates(yoProtoHandshake);

      int numberOfVariables = yoProtoHandshake.getVariableCount();
      int numberOfJointStateVariables = getNumberOfJointStateVariables(yoProtoHandshake);
      stateVariables = 1 + numberOfVariables + numberOfJointStateVariables;
   }

   private static List<YoRegistry> parseRegistries(YoProtoHandshake yoProtoHandshake)
   {
      YoRegistryDefinition rootDefinition = yoProtoHandshake.getRegistry(0);
      YoRegistry rootRegistry = new YoRegistry(rootDefinition.getName());

      List<YoRegistry> registryList = new ArrayList<>();
      registryList.add(rootRegistry);

      for (int i = 1; i < yoProtoHandshake.getRegistryCount(); i++)
      {
         YoRegistryDefinition registryDefinition = yoProtoHandshake.getRegistry(i);
         YoRegistry registry = new YoRegistry(registryDefinition.getName());
         registryList.add(registry);
         registryList.get(registryDefinition.getParent()).addChild(registry);
      }

      return registryList;
   }

   @SuppressWarnings("rawtypes")
   private static List<YoVariable> parseVariables(YoProtoHandshake yoProtoHandshake, List<YoRegistry> registryList)
   {
      List<YoVariable> variableList = new ArrayList<>();
      for (YoVariableDefinition yoVariableDefinition : yoProtoHandshake.getVariableList())
      {
         String name = yoVariableDefinition.getName();
         int registryIndex = yoVariableDefinition.getRegistry();
         YoRegistry parent = registryList.get(registryIndex);

         YoVariableDefinition.YoProtoType type = yoVariableDefinition.getType();
         switch (type)
         {
            case DoubleYoVariable:
               YoDouble doubleVar = new YoDouble(name, parent);
               variableList.add(doubleVar);
               break;

            case IntegerYoVariable:
               YoInteger intVar = new YoInteger(name, parent);
               variableList.add(intVar);
               break;

            case BooleanYoVariable:
               YoBoolean boolVar = new YoBoolean(name, parent);
               variableList.add(boolVar);
               break;

            case LongYoVariable:
               YoLong longVar = new YoLong(name, parent);
               variableList.add(longVar);
               break;

            case EnumYoVariable:
               List<String> values = yoVariableDefinition.getEnumValuesList();
               String[] names = values.toArray(new String[values.size()]);
               boolean allowNullValues = !yoVariableDefinition.hasAllowNullValues() || yoVariableDefinition.getAllowNullValues();
               YoEnum enumVar = new YoEnum(name, "", parent, allowNullValues, names);
               variableList.add(enumVar);
               break;

            default:
               throw new RuntimeException("Unknown YoVariable type: " + type.name());
         }
      }

      return variableList;
   }

   private int getNumberOfJointStateVariables(YoProtoHandshake yoProtoHandshake)
   {
      int numberOfJointStates = 0;
      for (int i = 0; i < yoProtoHandshake.getJointCount(); i++)
      {
         JointDefinition joint = yoProtoHandshake.getJoint(i);
         numberOfJointStates += JointState.getNumberOfVariables(convertJointType(joint.getType()));
      }
      return numberOfJointStates;
   }

   private void addJointStates(YoProtoHandshake yoProtoHandshake)
   {
      for (int i = 0; i < yoProtoHandshake.getJointCount(); i++)
      {
         JointDefinition joint = yoProtoHandshake.getJoint(i);
         jointStates.add(JointState.createJointState(joint.getName(), convertJointType(joint.getType())));
      }
   }

   @Override
   public void parseFrom(Handshake handshake) throws IOException
   {
      throw new RuntimeException("Not implemented");
   }
}
