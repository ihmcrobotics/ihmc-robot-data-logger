package us.ihmc.robotDataLogger.handshake;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.google.protobuf.InvalidProtocolBufferException;

import us.ihmc.commons.PrintTools;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearanceRGBColor;
import us.ihmc.graphicsDescription.color.MutableColor;
import us.ihmc.graphicsDescription.plotting.artifact.Artifact;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsList;
import us.ihmc.graphicsDescription.yoGraphics.plotting.ArtifactList;
import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.JointType;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake;
import us.ihmc.robotDataLogger.handshake.generated.YoProtoHandshakeProto.YoProtoHandshake.DynamicGraphicMessage;
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

/**
 * Depracated class to support legacy log files that still contain a description based on a
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
      addGraphicObjects(yoProtoHandshake);

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

   private void addGraphicObjects(YoProtoHandshake yoProtoHandshake)
   {
      HashMap<String, YoGraphicsList> dgoListMap = new HashMap<>();
      String listName;
      YoGraphicsList dgoList;
      for (int i = 0; i < yoProtoHandshake.getGraphicObjectCount(); i++)
      {
         listName = "default";
         if (yoProtoHandshake.getGraphicObject(i).hasListName())
         {
            if (!yoProtoHandshake.getGraphicObject(i).getListName().isEmpty())
               listName = yoProtoHandshake.getGraphicObject(i).getListName();
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
            dgoList.add((YoGraphic) getRemoteGraphic(yoProtoHandshake.getGraphicObject(i)));
         }
         catch (Exception e)
         {
            PrintTools.error(this, "Got exception: " + e.getClass().getSimpleName() + " when loading a YoGraphic.");
         }
      }

      for (String list : dgoListMap.keySet())
      {
         scs1YoGraphics.registerYoGraphicsList(dgoListMap.get(list));
      }

      ArtifactList artifactList = new ArtifactList("remote");
      for (int i = 0; i < yoProtoHandshake.getArtifactCount(); i++)
      {
         try
         {
            artifactList.add((Artifact) getRemoteGraphic(yoProtoHandshake.getArtifact(i)));
         }
         catch (Exception e)
         {
            PrintTools.error(this, "Got exception: " + e.getClass().getSimpleName() + " when loading a Artifact.");
         }
      }
      scs1YoGraphics.registerArtifactList(artifactList);
   }

   private RemoteYoGraphic getRemoteGraphic(DynamicGraphicMessage msg)
   {
      int registrationID = msg.getType();

      String name = msg.getName();
      YoVariable[] vars = new YoVariable[msg.getYoIndexCount()];
      for (int v = 0; v < vars.length; v++)
         vars[v] = variables.get(msg.getYoIndex(v));

      double[] consts = ArrayUtils.toPrimitive(msg.getConstantList().toArray(new Double[msg.getConstantCount()]));

      AppearanceDefinition appearance = new YoAppearanceRGBColor(Color.red, 0.0);
      if (msg.hasAppearance())
      {
         appearance = new YoAppearanceRGBColor(new MutableColor((float) msg.getAppearance().getX(),
                                                                (float) msg.getAppearance().getY(),
                                                                (float) msg.getAppearance().getZ()),
                                               msg.getAppearance().getTransparency());
      }

      return yoGraphicFromMessage(registrationID, name, vars, consts, appearance);
   }

   @Override
   public void parseFrom(Handshake handshake) throws IOException
   {
      throw new RuntimeException("Not implemented");
   }
}
