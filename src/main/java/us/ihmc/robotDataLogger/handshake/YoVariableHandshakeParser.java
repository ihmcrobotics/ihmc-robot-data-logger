package us.ihmc.robotDataLogger.handshake;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphic;
import us.ihmc.graphicsDescription.yoGraphics.RemoteYoGraphicFactory;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.robotDataLogger.Handshake;
import us.ihmc.robotDataLogger.HandshakeFileType;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.euclid.referenceFrame.interfaces.FrameIndexMap;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

public abstract class YoVariableHandshakeParser
{
   private final RemoteYoGraphicFactory yoGraphicFactory = new RemoteYoGraphicFactory();

   @SuppressWarnings("deprecation")
   public static YoVariableHandshakeParser create(HandshakeFileType type)
   {
      if (type == null)
      {
         System.err.println("Handshake file type is null. Defaulting to PROTOBUFFER");
         type = HandshakeFileType.PROTOBUFFER;
      }

      switch (type)
      {
         case IDL_CDR:
         case IDL_YAML:
            return new IDLYoVariableHandshakeParser(type);
         case PROTOBUFFER:
            return new ProtoBufferYoVariableHandshakeParser();
         default:
            throw new RuntimeException("Not implemented");
      }
   }

   public static int getNumberOfStateVariables(HandshakeFileType type, byte[] data) throws IOException
   {
      YoVariableHandshakeParser parser = create(type);
      parser.parseFrom(data);
      return parser.getNumberOfStates();
   }

   protected final YoGraphicsListRegistry scs1YoGraphics = new YoGraphicsListRegistry();
   protected List<YoGraphicGroupDefinition> scs2YoGraphics;
   protected final ArrayList<JointState> jointStates = new ArrayList<>();
   protected double dt;
   protected int stateVariables;
   protected int numberOfVariables;
   protected int numberOfJointStateVariables;
   protected List<YoRegistry> registries = new ArrayList<>();
   protected List<YoVariable> variables = new ArrayList<>();
   protected FrameIndexMap frameIndexMap;

   public abstract void parseFrom(Handshake handshake) throws IOException;

   public abstract void parseFrom(byte[] handShake) throws IOException;

   public YoVariableHandshakeParser()
   {
   }

   public YoRegistry getRootRegistry()
   {
      return registries.get(0);
   }

   public FrameIndexMap getFrameIndexMap()
   {
      return frameIndexMap;
   }

   public List<YoRegistry> getRegistries()
   {
      return Collections.unmodifiableList(registries);
   }

   public List<JointState> getJointStates()
   {
      return Collections.unmodifiableList(jointStates);
   }

   public List<YoVariable> getYoVariablesList()
   {
      return Collections.unmodifiableList(variables);
   }

   public YoGraphicsListRegistry getSCS1YoGraphics()
   {
      return scs1YoGraphics;
   }

   public List<YoGraphicGroupDefinition> getSCS2YoGraphics()
   {
      return scs2YoGraphics;
   }

   public double getDt()
   {
      return dt;
   }

   public int getBufferSize()
   {
      return stateVariables * 8;
   }

   public int getNumberOfStates()
   {
      return stateVariables;

   }

   public int getNumberOfVariables()
   {
      return numberOfVariables;
   }

   public int getNumberOfJointStateVariables()
   {
      return numberOfJointStateVariables;
   }

   protected RemoteYoGraphic yoGraphicFromMessage(int registrationID, String name, YoVariable[] vars, double[] consts, AppearanceDefinition appearance)
   {
      return yoGraphicFactory.yoGraphicFromMessage(registrationID, name, vars, consts, appearance);
   }

}