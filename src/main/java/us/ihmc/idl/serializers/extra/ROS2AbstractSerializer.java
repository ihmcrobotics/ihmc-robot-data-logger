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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import us.ihmc.jros2.ROS2Message;

/**
 * Abstract entry point to serialize/deserialize ROS2Message objects using Jackson modules.
 *
 * This replaces the TopicDataType-based serializers with a simpler approach that works
 * directly with ROS2Message objects that have CDRBuffer serialize/deserialize methods.
 *
 * Use [format]Serializer for concrete implementations (JSON, YAML, Properties).
 *
 * @author Generated
 *
 * @param <T> ROS2Message type
 */
public abstract class ROS2AbstractSerializer<T extends ROS2Message<T>>
{
   protected final ObjectMapper mapper;
   protected final Class<T> messageClass;
   protected final String messageName;
   private boolean addTypeAsRootNode = true;

   protected ROS2AbstractSerializer(Class<T> messageClass, String messageName, ObjectMapper mapper)
   {
      this.messageClass = messageClass;
      this.messageName = messageName;
      this.mapper = mapper;
   }

   /**
    * Function to enable or disable adding the message type name as the root node of the exported file
    *
    * Default: [Enabled]
    *
    * @param addTypeAsRootNode whether to add type as root node
    */
   public void setAddTypeAsRootNode(boolean addTypeAsRootNode)
   {
      this.addTypeAsRootNode = addTypeAsRootNode;
   }

   /**
    * Serialize data from ROS2Message representation using Jackson.
    *
    * @param data ROS2Message to serialize
    * @return String serialized representation of the data
    * @throws IOException if serialization fails
    */
   public String serializeToString(T data) throws IOException
   {
      try
      {
         return mapper.writeValueAsString(serializeToNode(data));
      }
      catch (JsonProcessingException e)
      {
         throw new IOException("Invalid call to Jackson made internally in the serializer. Please file a bug report", e);
      }
   }

   /**
    * Serialize data from ROS2Message representation using Jackson.
    *
    * @param data ROS2Message to serialize
    * @return byte array serialized representation of the data
    * @throws IOException if serialization fails
    */
   public byte[] serializeToBytes(T data) throws IOException
   {
      try
      {
         return mapper.writeValueAsBytes(serializeToNode(data));
      }
      catch (JsonProcessingException e)
      {
         throw new IOException("Invalid call to Jackson made internally in the serializer. Please file a bug report", e);
      }
   }

   /**
    * Serialize data from ROS2Message representation to a file.
    *
    * @param target file to write to
    * @param data ROS2Message to serialize
    * @throws IOException if serialization fails
    */
   public void serialize(File target, T data) throws IOException
   {
      mapper.writeValue(target, serializeToNode(data));
   }

   /**
    * Serialize data from ROS2Message representation to an output stream.
    *
    * @param target output stream to write to
    * @param data ROS2Message to serialize
    * @throws IOException if serialization fails
    */
   public void serialize(OutputStream target, T data) throws IOException
   {
      mapper.writeValue(target, serializeToNode(data));
   }

   /**
    * Serialize data from ROS2Message representation to a writer.
    *
    * @param target writer to write to
    * @param data ROS2Message to serialize
    * @throws IOException if serialization fails
    */
   public void serialize(Writer target, T data) throws IOException
   {
      mapper.writeValue(target, serializeToNode(data));
   }

   protected ObjectNode serializeToNode(T data) throws IOException
   {
      ObjectNode root = mapper.createObjectNode();
      ObjectNode node;

      if (addTypeAsRootNode)
      {
         node = root.putObject(messageName);
      }
      else
      {
         node = root;
      }

      CDRInterchangeSerializer serializer = new CDRInterchangeSerializer(node);
      serializer.serializeMessageFields(data, serializer);

      return root;
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source reader to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(Reader source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source input stream to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(InputStream source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source byte array to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(byte[] source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source string to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(String source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source file to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(File source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   /**
    * Deserialize to ROS2Message representation.
    *
    * @param source URL to read from
    * @return deserialized message or null if input is empty
    * @throws IOException if deserialization fails
    */
   public T deserialize(URL source) throws IOException
   {
      return deserializeFromNode(mapper.readTree(source));
   }

   protected T deserializeFromNode(JsonNode root) throws IOException
   {
      if (root == null)
      {
         return null;
      }

      JsonNode node = resolveMessageNode(root);

      if (node != null && node.isObject())
      {
         CDRInterchangeSerializer serializer = new CDRInterchangeSerializer((ObjectNode) node);
         T message = ROS2Message.createInstance(messageClass);
         serializer.deserializeMessageFields(message, serializer);
         if (customDeserializationHandler != null)
         {
            customDeserializationHandler.handle(node, message);
         }
         return message;
      }
      else
      {
         return null;
      }
   }

   private JsonNode resolveMessageNode(JsonNode root)
   {
      if (!addTypeAsRootNode)
      {
         JsonNode legacyNode = root.get(legacyIdlRootName(messageClass));
         if (legacyNode != null)
         {
            return legacyNode;
         }

         JsonNode typedNode = root.get(messageName);
         if (typedNode != null)
         {
            return typedNode;
         }

         if (root.isObject() && root.size() == 1)
         {
            JsonNode onlyEntry = root.elements().next();
            if (onlyEntry.isObject())
            {
               return onlyEntry;
            }
         }

         return root;
      }

      JsonNode node = root.get(messageName);
      if (node != null)
      {
         return node;
      }

      // Pre-jros2 IDL YAML used us::ihmc::robotDataLogger::MessageName as the root key.
      node = root.get(legacyIdlRootName(messageClass));
      if (node != null)
      {
         return node;
      }

      // Some files wrap the message in a single unknown root entry.
      if (root.isObject() && root.size() == 1)
      {
         JsonNode onlyEntry = root.elements().next();
         if (onlyEntry.isObject())
         {
            return onlyEntry;
         }
      }

      return null;
   }

   private static String legacyIdlRootName(Class<?> messageClass)
   {
      return "us::ihmc::robotDataLogger::" + messageClass.getSimpleName();
   }

   private CustomDeserializationHandler customDeserializationHandler = null;

   public void setCustomDeserializationHandler(CustomDeserializationHandler customDeserializationHandler)
   {
      this.customDeserializationHandler = customDeserializationHandler;
   }
}
