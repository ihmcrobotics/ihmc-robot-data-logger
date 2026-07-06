package us.ihmc.robotDataLogger.handshake;

import logger_msgs.Handshake;
import logger_msgs.HandshakeFileType;
import logger_msgs.MessageTypes;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicGroupDefinition;
import us.ihmc.yoVariables.euclid.referenceFrame.interfaces.FrameIndexMap;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class YoVariableHandshakeParser
{

   @SuppressWarnings("deprecation")
   public static YoVariableHandshakeParser create(HandshakeFileType type)
   {
      if (type == null)
      {
         System.err.println("Handshake file type is null. Defaulting to IDL_YAML");
         type = MessageTypes.IDL_YAML;
      }

      return create(type.getType());
   }

   public static YoVariableHandshakeParser create(byte handshakeFileType)
   {
      switch (handshakeFileType)
      {
         case HandshakeFileType.IDL_CDR:
         case HandshakeFileType.IDL_YAML:
            return new IDLYoVariableHandshakeParser(MessageTypes.handshakeFileType(handshakeFileType));
         default:
            throw new RuntimeException("Not implemented");
      }
   }

   public static int getNumberOfStateVariables(HandshakeFileType type, byte[] data) throws IOException
   {
      return getNumberOfStateVariables(type.getType(), data);
   }

   public static int getNumberOfStateVariables(byte handshakeFileType, byte[] data) throws IOException
   {
      YoVariableHandshakeParser parser = create(handshakeFileType);
      parser.parseFrom(data);
      return parser.getNumberOfStates();
   }

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
}