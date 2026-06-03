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

import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;

import us.ihmc.jros2.ROS2Message;

/**
 * Java Properties Serializer for ROS2Message objects. Serializes ROS2Messages to Java Properties representation using Jackson.
 *
 * By default, the type is not added to the serialized output.
 *
 * This replaces the TopicDataType-based PropertiesSerializer with a version that works directly with
 * ROS2Message objects.
 *
 * @author Generated
 *
 * @param <T> ROS2Message type
 */
public class ROS2PropertiesSerializer<T extends ROS2Message<T>> extends ROS2AbstractSerializer<T>
{
   /**
    * Create a Properties serializer for the given message type.
    *
    * @param messageClass class of the ROS2Message to serialize
    * @param messageName name of the message type (e.g., "logger_msgs::msg::dds_::Timestamp_")
    */
   public ROS2PropertiesSerializer(Class<T> messageClass, String messageName)
   {
      super(messageClass, messageName, new JavaPropsMapper());
      setAddTypeAsRootNode(false);
   }

   /**
    * Convenience constructor that extracts the message name from the message class.
    *
    * @param messageClass class of the ROS2Message to serialize
    */
   public ROS2PropertiesSerializer(Class<T> messageClass)
   {
      this(messageClass, extractMessageName(messageClass));
   }

   private static <T extends ROS2Message<T>> String extractMessageName(Class<T> messageClass)
   {
      try
      {
         java.lang.reflect.Field nameField = messageClass.getDeclaredField("name");
         nameField.setAccessible(true);
         return (String) nameField.get(null);
      }
      catch (Exception e)
      {
         return messageClass.getSimpleName();
      }
   }
}
