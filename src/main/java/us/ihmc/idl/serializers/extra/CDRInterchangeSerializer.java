/**
 * Copyright 2024 Florida Institute for Human and Machine Cognition (IHMC)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package us.ihmc.idl.serializers.extra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLIntSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.jros2.ROS2Message;

/**
 * Bridges between CDRBuffer serialization and Jackson JSON representation.
 *
 * This class provides a way to convert ROS2Message objects (which use CDRBuffer)
 * to and from Jackson ObjectNode representations for JSON, YAML, and Properties formats.
 *
 * @author Generated
 */
class CDRInterchangeSerializer
{
   private static final String[] LOGGER_MSGS_BYTE_ENUM_CLASSES = {
         "logger_msgs.CameraType",
         "logger_msgs.JointType",
         "logger_msgs.YoType",
         "logger_msgs.HandshakeFileType",
         "logger_msgs.LoadStatus",
         "logger_msgs.LogDataType",
   };

   private final ObjectNode node;

   CDRInterchangeSerializer(ObjectNode node)
   {
      this.node = node;
   }

   /**
    * Serialize a ROS2Message to the internal ObjectNode by first serializing to CDR,
    * then extracting field values using reflection.
    */
   public void serialize(String name, ROS2Message<?> message)
   {
      if (message == null)
      {
         node.putNull(name);
         return;
      }

      ObjectNode childNode = node.putObject(name);
      CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
      serializeMessageFields(message, childSerializer);
   }

   /**
    * Deserialize from the internal ObjectNode to a ROS2Message.
    */
   public void deserialize(String name, ROS2Message<?> message)
   {
      JsonNode childNode = node.get(name);
      if (childNode != null && childNode.isObject())
      {
         CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) childNode);
         deserializeMessageFields(message, childSerializer);
      }
   }

   /**
    * Serialize message fields to JSON using reflection to extract field values.
    */
   void serializeMessageFields(ROS2Message<?> message, CDRInterchangeSerializer serializer)
   {
      try
      {
         // Use reflection to access getters and serialize fields
         java.lang.reflect.Method[] methods = message.getClass().getMethods();
         for (java.lang.reflect.Method method : methods)
         {
            String methodName = method.getName();
            if (methodName.startsWith("get") && !methodName.equals("getClass") && method.getParameterCount() == 0)
            {
               // Skip methods ending with "AsString" to avoid duplicate serialization
//               if (methodName.endsWith("AsString"))
//               {
//                  continue;
//               }

               String fieldName = toFieldName(methodName.substring(3));
               Object value = method.invoke(message);

               // Special handling for certain fields to maintain backward compatibility
               serializer.serializeFieldWithContext(fieldName, value, message);
            }
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to serialize message fields", e);
      }
   }

   /**
    * Deserialize message fields from JSON using reflection to call setters, or populate nested
    * messages in-place when only a getter exists (embedded IDL structs).
    */
   void deserializeMessageFields(ROS2Message<?> message, CDRInterchangeSerializer serializer)
   {
      try
      {
         java.util.Iterator<String> fieldNames = serializer.node.fieldNames();
         while (fieldNames.hasNext())
         {
            String fieldName = fieldNames.next();
            if (deserializeFieldIntoMessage(message, serializer, fieldName))
            {
               continue;
            }
            if (deserializeSequenceIntoMessage(message, serializer, fieldName))
            {
               continue;
            }
            if (deserializeLegacyEmbeddedScalar(message, serializer, fieldName))
            {
               continue;
            }

            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            java.lang.reflect.Method[] methods = message.getClass().getMethods();
            for (java.lang.reflect.Method method : methods)
            {
               if (method.getName().equals(setterName) && method.getParameterCount() == 1)
               {
                  Class<?> paramType = method.getParameterTypes()[0];
                  Object value = serializer.deserializeField(fieldName, paramType, fieldName);
                  if (value != null)
                  {
                     method.invoke(message, value);
                  }
                  break;
               }
            }
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to deserialize message fields", e);
      }
   }

   private boolean deserializeFieldIntoMessage(ROS2Message<?> message, CDRInterchangeSerializer serializer, String fieldName) throws Exception
   {
      JsonNode fieldNode = serializer.node.get(fieldName);
      if (fieldNode == null || !fieldNode.isObject())
      {
         return false;
      }

      String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
      for (java.lang.reflect.Method method : message.getClass().getMethods())
      {
         if (method.getName().equals(getterName) && method.getParameterCount() == 0
             && ROS2Message.class.isAssignableFrom(method.getReturnType()))
         {
            ROS2Message<?> nestedMessage = (ROS2Message<?>) method.invoke(message);
            CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) fieldNode);
            childSerializer.deserializeMessageFields(nestedMessage, childSerializer);
            return true;
         }
      }

      return false;
   }

   private boolean deserializeSequenceIntoMessage(ROS2Message<?> message, CDRInterchangeSerializer serializer, String fieldName) throws Exception
   {
      JsonNode fieldNode = serializer.node.get(fieldName);
      if (fieldNode == null)
      {
         return false;
      }

      String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
      for (java.lang.reflect.Method method : message.getClass().getMethods())
      {
         if (!method.getName().equals(getterName) || method.getParameterCount() != 0)
         {
            continue;
         }

         Class<?> returnType = method.getReturnType();
         if (IDLObjectSequence.class.isAssignableFrom(returnType))
         {
            if (!fieldNode.isArray())
            {
               return false;
            }

            IDLObjectSequence<?> sequence = (IDLObjectSequence<?>) method.invoke(message);
            sequence.clear();
            populateObjectSequence(sequence, fieldNode);
            return true;
         }

         if (IDLStringSequence.class.isAssignableFrom(returnType))
         {
            IDLStringSequence sequence = (IDLStringSequence) method.invoke(message);
            sequence.clear();
            populateStringSequence(sequence, fieldNode);
            return true;
         }

         if (IDLIntSequence.class.isAssignableFrom(returnType))
         {
            if (!fieldNode.isArray())
            {
               return false;
            }

            IDLIntSequence sequence = (IDLIntSequence) method.invoke(message);
            sequence.clear();
            ArrayNode array = (ArrayNode) fieldNode;
            for (int i = 0; i < array.size(); i++)
            {
               sequence.add(array.get(i).asInt());
            }
            return true;
         }
      }

      return false;
   }

   private void populateStringSequence(IDLStringSequence sequence, JsonNode fieldNode)
   {
      if (fieldNode.isArray())
      {
         ArrayNode array = (ArrayNode) fieldNode;
         for (int i = 0; i < array.size(); i++)
         {
            sequence.add(array.get(i).asText());
         }
      }
      else if (fieldNode.isTextual())
      {
         for (String value : fieldNode.asText().split(","))
         {
            if (!value.isEmpty())
            {
               sequence.add(value);
            }
         }
      }
   }

   private void populateObjectSequence(IDLObjectSequence<?> sequence, JsonNode arrayNode) throws Exception
   {
      ArrayNode array = (ArrayNode) arrayNode;
      for (int i = 0; i < array.size(); i++)
      {
         JsonNode element = array.get(i);
         if (!element.isObject())
         {
            continue;
         }

         ROS2Message<?> item = (ROS2Message<?>) sequence.add();
         CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) element);
         childSerializer.deserializeMessageFields(item, childSerializer);
      }
   }

   private boolean deserializeLegacyEmbeddedScalar(ROS2Message<?> message, CDRInterchangeSerializer serializer, String fieldName) throws Exception
   {
      JsonNode fieldNode = serializer.node.get(fieldName);
      if (fieldNode == null || !fieldNode.isTextual())
      {
         return false;
      }

      if ("type".equals(fieldName))
      {
         for (java.lang.reflect.Method method : message.getClass().getMethods())
         {
            if (method.getName().equals("getType") && method.getParameterCount() == 0
                && "logger_msgs.YoType".equals(method.getReturnType().getName()))
            {
               Object yoType = method.invoke(message);
               yoType.getClass().getMethod("setType", byte.class).invoke(yoType, convertStringToYoType(fieldNode.asText()));
               return true;
            }
         }

         if (message instanceof logger_msgs.JointDefinition jointDefinition)
         {
            jointDefinition.setType(convertStringToJointType(fieldNode.asText()));
            return true;
         }
      }

      if ("loadStatus".equals(fieldName))
      {
         for (java.lang.reflect.Method method : message.getClass().getMethods())
         {
            if (method.getName().equals("setLoadStatus") && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == byte.class)
            {
               method.invoke(message, convertStringToLoadStatus(fieldNode.asText()));
               return true;
            }
         }
      }

      return false;
   }

   private byte convertStringToYoType(String typeName)
   {
      switch (typeName)
      {
         case "DoubleYoVariable":
            return logger_msgs.YoType.DOUBLEYOVARIABLE;
         case "BooleanYoVariable":
            return logger_msgs.YoType.BOOLEANYOVARIABLE;
         case "IntegerYoVariable":
            return logger_msgs.YoType.INTEGERYOVARIABLE;
         case "LongYoVariable":
            return logger_msgs.YoType.LONGYOVARIABLE;
         case "EnumYoVariable":
            return logger_msgs.YoType.ENUMYOVARIABLE;
         default:
            throw new RuntimeException("Unknown YoVariable type: " + typeName);
      }
   }

   private byte convertStringToJointType(String typeName)
   {
      switch (typeName)
      {
         case "SiXDoFJoint":
            return logger_msgs.JointType.SIXDOFJOINT;
         case "OneDoFJoint":
            return logger_msgs.JointType.ONEDOFJOINT;
         default:
            return parseLegacyByteEnum("type", typeName);
      }
   }

   private byte convertStringToLoadStatus(String statusName)
   {
      switch (statusName)
      {
         case "NoParameter":
            return logger_msgs.LoadStatus.NOPARAMETER;
         case "Unloaded":
            return logger_msgs.LoadStatus.UNLOADED;
         case "Default":
            return logger_msgs.LoadStatus.DEFAULT;
         case "Loaded":
            return logger_msgs.LoadStatus.LOADED;
         default:
            throw new RuntimeException("Unknown load status: " + statusName);
      }
   }

   private String toFieldName(String getterSuffix)
   {
      if (getterSuffix.isEmpty())
      {
         return getterSuffix;
      }

      // Handle trailing underscore (common in generated code)
      if (getterSuffix.endsWith("_"))
      {
         getterSuffix = getterSuffix.substring(0, getterSuffix.length() - 1);
      }

      return Character.toLowerCase(getterSuffix.charAt(0)) + getterSuffix.substring(1);
   }

   private void serializeFieldWithContext(String name, Object value, ROS2Message<?> message)
   {
      // Special handling for YoType to convert byte to string representation
      if (value != null && value.getClass().getName().equals("logger_msgs.YoType"))
      {
         try
         {
            java.lang.reflect.Method getTypeMethod = value.getClass().getMethod("getType");
            byte typeValue = (byte) getTypeMethod.invoke(value);
            String typeName = convertYoTypeToString(typeValue);
            node.put(name, typeName);
            return;
         }
         catch (Exception e)
         {
            // Fall through to normal serialization
         }
      }

      // Special handling for loadStatus field to convert byte to string
      if (name.equals("loadStatus") && value instanceof Byte)
      {
         String statusName = convertLoadStatusToString((Byte) value);
         node.put(name, statusName);
         return;
      }

      // Default serialization
      serializeField(name, value);
   }

   private String convertYoTypeToString(byte type)
   {
      switch (type)
      {
         case 0: return "DoubleYoVariable";
         case 1: return "BooleanYoVariable";
         case 2: return "IntegerYoVariable";
         case 3: return "LongYoVariable";
         case 4: return "EnumYoVariable";
         default: return "DoubleYoVariable";
      }
   }

   private String convertLoadStatusToString(byte status)
   {
      switch (status)
      {
         case 0: return "NoParameter";
         case 1: return "Unloaded";
         case 2: return "Default";
         case 3: return "Loaded";
         default: return "NoParameter";
      }
   }

   private void serializeField(String name, Object value)
   {
      if (value == null)
      {
         node.putNull(name);
      }
      else if (value instanceof Boolean)
      {
         node.put(name, (Boolean) value);
      }
      else if (value instanceof Byte)
      {
         node.put(name, (Byte) value);
      }
      else if (value instanceof Short)
      {
         node.put(name, (Short) value);
      }
      else if (value instanceof Integer)
      {
         node.put(name, (Integer) value);
      }
      else if (value instanceof Long)
      {
         node.put(name, (Long) value);
      }
      else if (value instanceof Float)
      {
         node.put(name, (Float) value);
      }
      else if (value instanceof Double)
      {
         node.put(name, (Double) value);
      }
      else if (value instanceof String)
      {
         node.put(name, (String) value);
      }
      else if (value instanceof StringBuilder)
      {
         node.put(name, value.toString());
      }
      else if (value instanceof Character)
      {
         node.put(name, String.valueOf(value));
      }
      else if (value instanceof Enum)
      {
         node.put(name, ((Enum<?>) value).name());
      }
      else if (value instanceof ROS2Message)
      {
         ObjectNode childNode = node.putObject(name);
         CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
         childSerializer.serializeMessageFields((ROS2Message<?>) value, childSerializer);
      }
      else if (value.getClass().isArray())
      {
         serializeArray(name, value);
      }
      else if (value instanceof java.util.List)
      {
         serializeList(name, (java.util.List<?>) value);
      }
      else if (value instanceof IDLStringSequence)
      {
         serializeIDLStringSequence(name, (IDLStringSequence) value);
      }
      else if (value instanceof IDLSequence)
      {
         serializeIDLSequence(name, (IDLSequence<?>) value);
      }
      else
      {
         // Try to serialize as a nested message
         ObjectNode childNode = node.putObject(name);
         CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
         try
         {
            childSerializer.serializeMessageFields((ROS2Message<?>) value, childSerializer);
         }
         catch (Exception e)
         {
            node.remove(name);
            node.put(name, value.toString());
         }
      }
   }

   private void serializeArray(String name, Object array)
   {
      ArrayNode arrayNode = node.putArray(name);
      Class<?> componentType = array.getClass().getComponentType();

      if (componentType == boolean.class)
      {
         for (boolean v : (boolean[]) array) arrayNode.add(v);
      }
      else if (componentType == byte.class)
      {
         for (byte v : (byte[]) array) arrayNode.add(v);
      }
      else if (componentType == short.class)
      {
         for (short v : (short[]) array) arrayNode.add(v);
      }
      else if (componentType == int.class)
      {
         for (int v : (int[]) array) arrayNode.add(v);
      }
      else if (componentType == long.class)
      {
         for (long v : (long[]) array) arrayNode.add(v);
      }
      else if (componentType == float.class)
      {
         for (float v : (float[]) array) arrayNode.add(v);
      }
      else if (componentType == double.class)
      {
         for (double v : (double[]) array) arrayNode.add(v);
      }
      else if (componentType == char.class)
      {
         for (char v : (char[]) array) arrayNode.add(String.valueOf(v));
      }
      else
      {
         for (Object v : (Object[]) array)
         {
            if (v == null)
            {
               arrayNode.addNull();
            }
            else if (v instanceof ROS2Message)
            {
               ObjectNode childNode = arrayNode.addObject();
               CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
               childSerializer.serializeMessageFields((ROS2Message<?>) v, childSerializer);
            }
            else
            {
               arrayNode.add(v.toString());
            }
         }
      }
   }

   private void serializeList(String name, java.util.List<?> list)
   {
      ArrayNode arrayNode = node.putArray(name);
      for (Object item : list)
      {
         if (item == null)
         {
            arrayNode.addNull();
         }
         else if (item instanceof Boolean)
         {
            arrayNode.add((Boolean) item);
         }
         else if (item instanceof Number)
         {
            if (item instanceof Integer) arrayNode.add((Integer) item);
            else if (item instanceof Long) arrayNode.add((Long) item);
            else if (item instanceof Float) arrayNode.add((Float) item);
            else if (item instanceof Double) arrayNode.add((Double) item);
            else if (item instanceof Short) arrayNode.add((Short) item);
            else if (item instanceof Byte) arrayNode.add((Byte) item);
            else arrayNode.add(item.toString());
         }
         else if (item instanceof String)
         {
            arrayNode.add((String) item);
         }
         else if (item instanceof ROS2Message)
         {
            ObjectNode childNode = arrayNode.addObject();
            CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
            childSerializer.serializeMessageFields((ROS2Message<?>) item, childSerializer);
         }
         else
         {
            arrayNode.add(item.toString());
         }
      }
   }

   private void serializeIDLStringSequence(String name, IDLStringSequence sequence)
   {
      ArrayNode arrayNode = node.putArray(name);
      for (int i = 0; i < sequence.size(); i++)
      {
         arrayNode.add(sequence.getAsString(i));
      }
   }

   private void serializeIDLSequence(String name, IDLSequence<?> sequence)
   {
      ArrayNode arrayNode = node.putArray(name);
      // IDLSequence is a generic sequence type, we need to handle it generically
      // For now, just serialize using reflection or toString
      try
      {
         // Try to get elements using reflection
         java.lang.reflect.Method getMethod = sequence.getClass().getMethod("get", int.class);
         for (int i = 0; i < sequence.size(); i++)
         {
            Object item = getMethod.invoke(sequence, i);
            if (item == null)
            {
               arrayNode.addNull();
            }
            else if (item instanceof Number)
            {
               if (item instanceof Integer) arrayNode.add((Integer) item);
               else if (item instanceof Long) arrayNode.add((Long) item);
               else if (item instanceof Float) arrayNode.add((Float) item);
               else if (item instanceof Double) arrayNode.add((Double) item);
               else if (item instanceof Short) arrayNode.add((Short) item);
               else if (item instanceof Byte) arrayNode.add((Byte) item);
               else arrayNode.add(item.toString());
            }
            else if (item instanceof String)
            {
               arrayNode.add((String) item);
            }
            else if (item instanceof StringBuilder)
            {
               arrayNode.add(item.toString());
            }
            else if (item instanceof Boolean)
            {
               arrayNode.add((Boolean) item);
            }
            else if (item instanceof ROS2Message)
            {
               ObjectNode childNode = arrayNode.addObject();
               CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer(childNode);
               childSerializer.serializeMessageFields((ROS2Message<?>) item, childSerializer);
            }
            else
            {
               arrayNode.add(item.toString());
            }
         }
      }
      catch (Exception e)
      {
         // Fallback: just add the toString representation
         for (int i = 0; i < sequence.size(); i++)
         {
            arrayNode.add(sequence.toString());
         }
      }
   }

   private Object deserializeField(String name, Class<?> type)
   {
      return deserializeField(name, type, name);
   }

   private Object deserializeField(String name, Class<?> type, String legacyEnumFieldName)
   {
      JsonNode fieldNode = node.get(name);
      if (fieldNode == null || fieldNode.isNull())
      {
         return null;
      }

      if (type == boolean.class || type == Boolean.class)
      {
         return fieldNode.asBoolean();
      }
      else if (type == byte.class || type == Byte.class)
      {
         if (fieldNode.isTextual())
         {
            return parseLegacyByteEnum(legacyEnumFieldName, fieldNode.asText());
         }
         return (byte) fieldNode.asInt();
      }
      else if (type == short.class || type == Short.class)
      {
         return (short) fieldNode.asInt();
      }
      else if (type == int.class || type == Integer.class)
      {
         return fieldNode.asInt();
      }
      else if (type == long.class || type == Long.class)
      {
         return fieldNode.asLong();
      }
      else if (type == float.class || type == Float.class)
      {
         return (float) fieldNode.asDouble();
      }
      else if (type == double.class || type == Double.class)
      {
         return fieldNode.asDouble();
      }
      else if (type == String.class)
      {
         return fieldNode.asText();
      }
      else if (type == StringBuilder.class)
      {
         return new StringBuilder(fieldNode.asText());
      }
      else if (type == char.class || type == Character.class)
      {
         String text = fieldNode.asText();
         return text.isEmpty() ? '\0' : text.charAt(0);
      }
      else if (type.isEnum())
      {
         String enumName = fieldNode.asText();
         for (Object enumConstant : type.getEnumConstants())
         {
            if (((Enum<?>) enumConstant).name().equals(enumName))
            {
               return enumConstant;
            }
         }
         return null;
      }
      else if (type.isArray())
      {
         return deserializeArray(fieldNode, type);
      }
      else if (type == IDLStringSequence.class)
      {
         return deserializeIDLStringSequence(fieldNode);
      }
      else if (IDLSequence.class.isAssignableFrom(type))
      {
         return deserializeIDLSequence(fieldNode, type);
      }
      else if (ROS2Message.class.isAssignableFrom(type))
      {
         try
         {
            ROS2Message<?> message = (ROS2Message<?>) type.getDeclaredConstructor().newInstance();
            if (fieldNode.isObject())
            {
               CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) fieldNode);
               childSerializer.deserializeMessageFields(message, childSerializer);
            }
            return message;
         }
         catch (Exception e)
         {
            throw new RuntimeException("Failed to deserialize nested message of type " + type, e);
         }
      }
      else
      {
         return null;
      }
   }

   private byte parseLegacyByteEnum(String fieldName, String value)
   {
      if ("handshakeFileType".equals(fieldName))
      {
         switch (value)
         {
            case "IDL_YAML":
               return logger_msgs.HandshakeFileType.IDL_YAML;
            case "IDL_CDR":
               return logger_msgs.HandshakeFileType.IDL_CDR;
            case "PROTOBUFFER":
               return 0;
            default:
               throw new RuntimeException("Unknown handshake file type: " + value);
         }
      }

      try
      {
         return (byte) Integer.parseInt(value);
      }
      catch (NumberFormatException e)
      {
         Byte resolved = lookupLoggerMsgsByteConstant(value);
         if (resolved != null)
         {
            return resolved;
         }
         throw new RuntimeException("Cannot parse byte field " + fieldName + " from value: " + value, e);
      }
   }

   private Byte lookupLoggerMsgsByteConstant(String constantName)
   {
      for (String className : LOGGER_MSGS_BYTE_ENUM_CLASSES)
      {
         try
         {
            java.lang.reflect.Field field = Class.forName(className).getField(constantName);
            if (field.getType() == byte.class
                && java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && java.lang.reflect.Modifier.isFinal(field.getModifiers()))
            {
               return field.getByte(null);
            }
         }
         catch (ReflectiveOperationException ignored)
         {
         }
      }
      return null;
   }

   private Object deserializeArray(JsonNode arrayNode, Class<?> arrayType)
   {
      if (!arrayNode.isArray())
      {
         return null;
      }

      ArrayNode array = (ArrayNode) arrayNode;
      Class<?> componentType = arrayType.getComponentType();
      int length = array.size();

      if (componentType == boolean.class)
      {
         boolean[] result = new boolean[length];
         for (int i = 0; i < length; i++) result[i] = array.get(i).asBoolean();
         return result;
      }
      else if (componentType == byte.class)
      {
         byte[] result = new byte[length];
         for (int i = 0; i < length; i++) result[i] = (byte) array.get(i).asInt();
         return result;
      }
      else if (componentType == short.class)
      {
         short[] result = new short[length];
         for (int i = 0; i < length; i++) result[i] = (short) array.get(i).asInt();
         return result;
      }
      else if (componentType == int.class)
      {
         int[] result = new int[length];
         for (int i = 0; i < length; i++) result[i] = array.get(i).asInt();
         return result;
      }
      else if (componentType == long.class)
      {
         long[] result = new long[length];
         for (int i = 0; i < length; i++) result[i] = array.get(i).asLong();
         return result;
      }
      else if (componentType == float.class)
      {
         float[] result = new float[length];
         for (int i = 0; i < length; i++) result[i] = (float) array.get(i).asDouble();
         return result;
      }
      else if (componentType == double.class)
      {
         double[] result = new double[length];
         for (int i = 0; i < length; i++) result[i] = array.get(i).asDouble();
         return result;
      }
      else if (componentType == char.class)
      {
         char[] result = new char[length];
         for (int i = 0; i < length; i++)
         {
            String text = array.get(i).asText();
            result[i] = text.isEmpty() ? '\0' : text.charAt(0);
         }
         return result;
      }
      else
      {
         Object[] result = (Object[]) java.lang.reflect.Array.newInstance(componentType, length);
         for (int i = 0; i < length; i++)
         {
            JsonNode element = array.get(i);
            if (element.isObject() && ROS2Message.class.isAssignableFrom(componentType))
            {
               try
               {
                  ROS2Message<?> message = (ROS2Message<?>) componentType.getDeclaredConstructor().newInstance();
                  CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) element);
                  childSerializer.deserializeMessageFields(message, childSerializer);
                  result[i] = message;
               }
               catch (Exception e)
               {
                  throw new RuntimeException("Failed to deserialize array element", e);
               }
            }
         }
         return result;
      }
   }

   private Object deserializeIDLStringSequence(JsonNode arrayNode)
   {
      if (!arrayNode.isArray())
      {
         return new IDLStringSequence();
      }

      ArrayNode array = (ArrayNode) arrayNode;
      IDLStringSequence sequence = new IDLStringSequence(array.size());
      for (int i = 0; i < array.size(); i++)
      {
         sequence.add(array.get(i).asText());
      }
      return sequence;
   }

   private Object deserializeIDLSequence(JsonNode arrayNode, Class<?> sequenceType)
   {
      if (!arrayNode.isArray())
      {
         try
         {
            return sequenceType.getDeclaredConstructor().newInstance();
         }
         catch (Exception e)
         {
            return null;
         }
      }

      ArrayNode array = (ArrayNode) arrayNode;
      try
      {
         // Create a new instance of the sequence
         IDLSequence<?> sequence = (IDLSequence<?>) sequenceType.getDeclaredConstructor().newInstance();

         // Try to find the add method
         java.lang.reflect.Method addMethod = null;
         for (java.lang.reflect.Method method : sequenceType.getMethods())
         {
            if (method.getName().equals("add") && method.getParameterCount() == 1)
            {
               addMethod = method;
               break;
            }
         }

         if (addMethod != null)
         {
            Class<?> paramType = addMethod.getParameterTypes()[0];
            for (int i = 0; i < array.size(); i++)
            {
               JsonNode element = array.get(i);
               Object value = deserializeValue(element, paramType);
               if (value != null)
               {
                  addMethod.invoke(sequence, value);
               }
            }
         }

         return sequence;
      }
      catch (Exception e)
      {
         return null;
      }
   }

   private Object deserializeValue(JsonNode node, Class<?> type)
   {
      if (node == null || node.isNull())
      {
         return null;
      }

      if (type == boolean.class || type == Boolean.class)
      {
         return node.asBoolean();
      }
      else if (type == byte.class || type == Byte.class)
      {
         return (byte) node.asInt();
      }
      else if (type == short.class || type == Short.class)
      {
         return (short) node.asInt();
      }
      else if (type == int.class || type == Integer.class)
      {
         return node.asInt();
      }
      else if (type == long.class || type == Long.class)
      {
         return node.asLong();
      }
      else if (type == float.class || type == Float.class)
      {
         return (float) node.asDouble();
      }
      else if (type == double.class || type == Double.class)
      {
         return node.asDouble();
      }
      else if (type == String.class)
      {
         return node.asText();
      }
      else if (type == StringBuilder.class)
      {
         return new StringBuilder(node.asText());
      }
      else if (ROS2Message.class.isAssignableFrom(type))
      {
         try
         {
            ROS2Message<?> message = (ROS2Message<?>) type.getDeclaredConstructor().newInstance();
            if (node.isObject())
            {
               CDRInterchangeSerializer childSerializer = new CDRInterchangeSerializer((ObjectNode) node);
               childSerializer.deserializeMessageFields(message, childSerializer);
            }
            return message;
         }
         catch (Exception e)
         {
            return null;
         }
      }
      else
      {
         return null;
      }
   }

   ObjectNode getNode()
   {
      return node;
   }
}
