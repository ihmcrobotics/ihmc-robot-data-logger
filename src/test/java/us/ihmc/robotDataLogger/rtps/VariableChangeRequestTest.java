package us.ihmc.robotDataLogger.rtps;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.pubsub.Domain;
import us.ihmc.pubsub.DomainFactory;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.pubsub.attributes.ParticipantAttributes;
import us.ihmc.pubsub.attributes.ReliabilityKind;
import us.ihmc.pubsub.common.LogLevel;
import us.ihmc.pubsub.common.MatchingInfo;
import us.ihmc.pubsub.common.SampleInfo;
import us.ihmc.pubsub.participant.Participant;
import us.ihmc.pubsub.publisher.Publisher;
import us.ihmc.pubsub.subscriber.Subscriber;
import us.ihmc.pubsub.subscriber.SubscriberListener;
import us.ihmc.robotDataLogger.VariableChangeRequest;
import us.ihmc.robotDataLogger.VariableChangeRequestPubSubType;

import static org.junit.jupiter.api.Assertions.*;

public class VariableChangeRequestTest
{

   @Test
   public void testReceiveChangedVariablesOneMessage() throws IOException
   {
      final VariableChangeRequest requestCopy = new VariableChangeRequest();
      AtomicInteger receivedMessages = new AtomicInteger(0);
      VariableChangeRequestPubSubType type = new VariableChangeRequestPubSubType();

      // The SubscriberListener listens for any updates being sent to the domain channel its listening on
      SubscriberListener<Double> listener = new SubscriberListener<>()
      {
         final VariableChangeRequest request = new VariableChangeRequest();

         @Override
         public void onSubscriptionMatched(Subscriber subscriber, MatchingInfo info)
         {
            if (info.getStatus() == MatchingInfo.MatchingStatus.MATCHED_MATCHING)
            {
               System.out.println("Connected " + info.getGuid());
            }
            else
            {
               System.out.println("Disconnected " + info.getGuid());
            }
         }

         // If any update on the domain channel is detected, this method will be called
         @Override
         public void onNewDataMessage(Subscriber subscriber)
         {
            if (subscriber.takeNextData(request, new SampleInfo()))
            {
               // Increment the amount or messages that have been received and copy the value to use with the asserts in the test
               receivedMessages.incrementAndGet();
               requestCopy.set(request);
            }

            System.out.println("Received: " + request);
         }
      };

      // The domain is how the subscriber and publisher talk to each other. If this isn't set up correctly then the message won't be received
      Domain domain = DomainFactory.getDomain(PubSubImplementation.FAST_RTPS);

      // Where on the domain the publishers and subscribers will be listening on
      Participant participant = domain.createParticipant(domain.createParticipantAttributes(1, "TestParticipant"));

      // Publisher and Subscriber are created here on the Participant with specific unique names in order to talk back and forth
      Publisher publisher = domain.createPublisher(participant, domain.createPublisherAttributes(participant,
                                                                                                 type,
                                                                                                 "testTopic",
                                                                                                 ReliabilityKind.RELIABLE.toQosKind(),
                                                                                                 "us.ihmc"));
      domain.createSubscriber(participant, domain.createSubscriberAttributes(participant,
                                                                             type,
                                                                             "testTopic",
                                                                             ReliabilityKind.RELIABLE.toQosKind(),
                                                                             "us.ihmc"), listener);

      ThreadTools.sleep(1000);

      // This is where the messages are updates and sent to the receiver, the test also checks that the sent and received values are the same
      VariableChangeRequest sentMessage = new VariableChangeRequest();

      for (int i = 0; i < 10; i++)
      {
         sentMessage.variableID_ = i + 100;
         sentMessage.requestedValue_ = i * 13.37;

         publisher.write(sentMessage);
         System.out.println("Writing: " + sentMessage);

         ThreadTools.sleep(1000);

         assertEquals(sentMessage.getRequestedValue(), requestCopy.getRequestedValue());
      }

      assertEquals(10, receivedMessages.get());
   }


   @Test
   public void testReceiveChangedVariablesMultipleMessages() throws IOException
   {
      AtomicInteger receivedMessages = new AtomicInteger(0);
      VariableChangeRequestPubSubType type = new VariableChangeRequestPubSubType();
      VariableChangeRequest requestCopy = new VariableChangeRequest();

      // The SubscriberListener listens for any updates being sent to the domain channel its listening on
      SubscriberListener<Double> listener = new SubscriberListener<>()
      {

         @Override
         public void onSubscriptionMatched(Subscriber subscriber, MatchingInfo info)
         {
            if (info.getStatus() == MatchingInfo.MatchingStatus.MATCHED_MATCHING)
            {
               System.out.println("Connected " + info.getGuid());
            }
            else
            {
               System.out.println("Disconnected " + info.getGuid());
            }
         }

         @Override
         public void onNewDataMessage(Subscriber subscriber)
         {
            VariableChangeRequest request = new VariableChangeRequest();
            SampleInfo info = new SampleInfo();
            if (subscriber.takeNextData(request, info))
            {
               receivedMessages.incrementAndGet();
               requestCopy.set(request);
            }

            // Even with multiple publishers, the subscriber if its listening to the same channel will receive updates from both
            System.out.println("Received: " + request);
         }
      };

      Domain domain = DomainFactory.getDomain(PubSubImplementation.FAST_RTPS);
      domain.setLogLevel(LogLevel.WARNING);

      ParticipantAttributes attr = domain.createParticipantAttributes(1, "TestParticipant");
      attr.useOnlySharedMemoryTransport();

      Participant participant = domain.createParticipant(attr);

      // The amount of publishers can increase with just the need for one subscriber
      Publisher publisher1 = domain.createPublisher(participant, domain.createPublisherAttributes(participant,
                                                                                                  type,
                                                                                                  "testTopic",
                                                                                                  ReliabilityKind.RELIABLE.toQosKind(),
                                                                                                  "us/ihmc"));
      Publisher publisher2 = domain.createPublisher(participant, domain.createPublisherAttributes(participant,
                                                                                                  type,
                                                                                                  "testTopic",
                                                                                                  ReliabilityKind.RELIABLE.toQosKind(),
                                                                                                  "us/ihmc"));

      Publisher publisher3 = domain.createPublisher(participant, domain.createPublisherAttributes(participant,
                                                                                                  type,
                                                                                                  "testTopic",
                                                                                                  ReliabilityKind.RELIABLE.toQosKind(),
                                                                                                  "us/ihmc"));

      domain.createSubscriber(participant, domain.createSubscriberAttributes(participant,
                                                                             type,
                                                                             "testTopic",
                                                                             ReliabilityKind.RELIABLE.toQosKind(),
                                                                             "us/ihmc"), listener);

      ThreadTools.sleep(1000);

      // This is where the testing is happening, messages are sent to the subscriber and checks are done to see if the messages are correct
      VariableChangeRequest messageFirst = new VariableChangeRequest();
      VariableChangeRequest messageSecond = new VariableChangeRequest();
      VariableChangeRequest messageThird = new VariableChangeRequest();

      for (int i = 0; i < 10; i++)
      {
         messageFirst.setVariableID(i + 100);
         messageFirst.setRequestedValue(i * 1.1);

         // Send the values for the first message, and check that the received values are the same
         System.out.println("Writing First: " + messageFirst);
         publisher1.write(messageFirst);
         ThreadTools.sleep(100);

         assertEquals(messageFirst.getRequestedValue(), requestCopy.getRequestedValue());

         messageSecond.setVariableID(i + 200);
         messageSecond.setRequestedValue(i * 2.2);

         // Send the values for the next message, and check that the received values are the same
         System.out.println("Writing Second: " + messageSecond);
         publisher2.write(messageSecond);
         ThreadTools.sleep(100);

         assertEquals(messageSecond.getRequestedValue(), requestCopy.getRequestedValue());

         messageThird.setVariableID(i + 300);
         messageThird.setRequestedValue(i * 4.4);

         // Send the values for the next message, and check that the received values are the same
         System.out.println("Writing Third: " + messageSecond);
         publisher3.write(messageThird);
         ThreadTools.sleep(100);

         assertEquals(messageThird.getRequestedValue(), requestCopy.getRequestedValue());
      }

      assertEquals(30, receivedMessages.get());
   }

   @Test
   public void testCheckEquals()
   {
      boolean result;
      VariableChangeRequest dataOne = new VariableChangeRequest();
      VariableChangeRequest dataTwo;

      for (int i = 0; i < 12; i++)
      {
         dataOne.setVariableID(i + 24);
         dataOne.setRequestedValue(i * 3.6);

         dataTwo = new VariableChangeRequest(dataOne);

         result = dataOne.equals(dataTwo);

         assertTrue(result);
      }
   }
}
