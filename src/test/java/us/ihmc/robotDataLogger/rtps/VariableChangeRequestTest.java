package us.ihmc.robotDataLogger.rtps;

import logger_msgs.VariableChangeRequest;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Topic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VariableChangeRequestTest
{
   private static final ROS2Topic<VariableChangeRequest> TEST_TOPIC = new ROS2Topic<VariableChangeRequest>().withType(VariableChangeRequest.class)
                                                                                                            .appendedWith("variable_change_request_test");

   @Test
   public void testReceiveChangedVariablesOneMessage()
   {
      AtomicInteger receivedMessages = new AtomicInteger(0);
      AtomicReference<VariableChangeRequest> lastReceived = new AtomicReference<>(new VariableChangeRequest());

      try (ROS2Node node = new ROS2Node("test_node_one_message", 1))
      {
         node.createSubscriptionSampler(TEST_TOPIC, message ->
         {
            VariableChangeRequest copy = new VariableChangeRequest();
            copy.set(message);
            lastReceived.set(copy);
            receivedMessages.incrementAndGet();
            System.out.println("Received: " + message);
         }, ROS2QoSProfile.RELIABLE);

         ROS2Publisher<VariableChangeRequest> publisher = node.createPublisher(TEST_TOPIC, ROS2QoSProfile.RELIABLE);

         ThreadTools.sleep(1000);

         VariableChangeRequest sentMessage = new VariableChangeRequest();

         for (int i = 0; i < 10; i++)
         {
            sentMessage.setVariableID(i + 100);
            sentMessage.setRequestedValue(i * 13.37);

            publisher.publish(sentMessage);
            System.out.println("Writing: " + sentMessage);

            ThreadTools.sleep(1000);

            assertEquals(sentMessage.getRequestedValue(), lastReceived.get().getRequestedValue());
         }

         assertEquals(10, receivedMessages.get());
      }
   }

   @Test
   public void testReceiveChangedVariablesMultipleMessages()
   {
      AtomicInteger receivedMessages = new AtomicInteger(0);
      AtomicReference<VariableChangeRequest> lastReceived = new AtomicReference<>(new VariableChangeRequest());

      try (ROS2Node node = new ROS2Node("test_node_multiple_messages", 1))
      {
         node.createSubscriptionSampler(TEST_TOPIC, message ->
         {
            VariableChangeRequest copy = new VariableChangeRequest();
            copy.set(message);
            lastReceived.set(copy);
            receivedMessages.incrementAndGet();
            System.out.println("Received: " + message);
         }, ROS2QoSProfile.RELIABLE);

         ROS2Publisher<VariableChangeRequest> publisher1 = node.createPublisher(TEST_TOPIC, ROS2QoSProfile.RELIABLE);
         ROS2Publisher<VariableChangeRequest> publisher2 = node.createPublisher(TEST_TOPIC, ROS2QoSProfile.RELIABLE);
         ROS2Publisher<VariableChangeRequest> publisher3 = node.createPublisher(TEST_TOPIC, ROS2QoSProfile.RELIABLE);

         ThreadTools.sleep(1000);

         VariableChangeRequest messageFirst = new VariableChangeRequest();
         VariableChangeRequest messageSecond = new VariableChangeRequest();
         VariableChangeRequest messageThird = new VariableChangeRequest();

         for (int i = 0; i < 10; i++)
         {
            messageFirst.setVariableID(i + 100);
            messageFirst.setRequestedValue(i * 1.1);

            System.out.println("Writing First: " + messageFirst);
            publisher1.publish(messageFirst);
            ThreadTools.sleep(100);

            assertEquals(messageFirst.getRequestedValue(), lastReceived.get().getRequestedValue());

            messageSecond.setVariableID(i + 200);
            messageSecond.setRequestedValue(i * 2.2);

            System.out.println("Writing Second: " + messageSecond);
            publisher2.publish(messageSecond);
            ThreadTools.sleep(100);

            assertEquals(messageSecond.getRequestedValue(), lastReceived.get().getRequestedValue());

            messageThird.setVariableID(i + 300);
            messageThird.setRequestedValue(i * 4.4);

            System.out.println("Writing Third: " + messageThird);
            publisher3.publish(messageThird);
            ThreadTools.sleep(100);

            assertEquals(messageThird.getRequestedValue(), lastReceived.get().getRequestedValue());
         }

         assertEquals(30, receivedMessages.get());
      }
   }

   @Test
   public void testCheckCopy()
   {
      VariableChangeRequest dataOne = new VariableChangeRequest();
      VariableChangeRequest dataTwo = new VariableChangeRequest();

      for (int i = 0; i < 12; i++)
      {
         dataOne.setVariableID(i + 24);
         dataOne.setRequestedValue(i * 3.6);

         dataTwo.set(dataOne);

         assertEquals(dataOne.getVariableID(), dataTwo.getVariableID());
         assertEquals(dataOne.getRequestedValue(), dataTwo.getRequestedValue());
      }
   }
}
