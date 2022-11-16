package us.ihmc.robotDataLogger.dataBuffers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import us.ihmc.commons.Conversions;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.mecano.multiBodySystem.RevoluteJoint;
import us.ihmc.mecano.multiBodySystem.RigidBody;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.pubsub.common.SerializedPayload;
import us.ihmc.robotDataLogger.jointState.JointHolder;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFJointHolder;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistrySendBufferTest
{
   public static final int MAX_PACKET_SIZE = 65000;

   /**
    * Calculate the number of variables in each segment
    *
    * @param variables   total number of variables
    * @param jointStates total number of joint states
    * @return array of variables in each segment
    */
   public static int[] calculateLogSegmentSizes(int variables, int jointStates)
   {
      CompressionImplementation compressor = CompressionImplementationFactory.instance();
      int maxCompressedSize = compressor.maxCompressedLength(variables * 8);
      int maxSize = CustomLogDataPublisherType.getTypeSize(maxCompressedSize, jointStates);

      if (maxSize > MAX_PACKET_SIZE)
      {
         // Space available in bytes for variables
         int remaining = MAX_PACKET_SIZE - CustomLogDataPublisherType.getTypeSize(0, 0);

         // Total variables in packet. Adding the number of uncompressed joint states gives us a slightly larger estimate of how much space we need.
         int totalVariables = variables + jointStates;

         // Variables we can store in each packet, rounding down
         int variablesPerPacket = compressor.minimumDecompressedLength(remaining) / 8;

         // Number of packets needed to store all variables, including joint states. Rounding up.
         int packets = (totalVariables - 1) / variablesPerPacket + 1;

         // Variables per packet, rounding up.
         int averageVariablesPerPacket = (totalVariables - 1) / packets + 1;

         int[] variableCounts = new int[packets];
         // If there are more joint states than variables per packet put all variables in subsequent packets
         if (averageVariablesPerPacket <= jointStates)
         {
            variableCounts[0] = 0;
            int variablesPerResultingPacket = (variables - 1) / (packets - 1) + 1;
            Arrays.fill(variableCounts, 1, variableCounts.length, variablesPerResultingPacket);
         }
         else // Average it out
         {
            variableCounts[0] = averageVariablesPerPacket - jointStates;
            int variablesPerResultingPacket = (variables - variableCounts[0] - 1) / (packets - 1) + 1;
            Arrays.fill(variableCounts, 1, variableCounts.length, variablesPerResultingPacket);
         }

         // Account for rounding errors, make sure we have enough space
         int sum = 0;
         for (int val : variableCounts)
         {
            sum += val;
         }
         variableCounts[variableCounts.length - 1] += variables - sum;

         return variableCounts;
      }
      else
      {
         return new int[] {variables};
      }
   }

   public static int calculateMaximumNumberOfVariables(int variables, int jointStates)
   {
      int[] segmentSizes = calculateLogSegmentSizes(variables, jointStates);
      int max = 0;
      for (int size : segmentSizes)
      {
         if (size > max)
         {
            max = size;
         }
      }
      return max;
   }

   @Test
   public void testYoVariables() throws IOException
   {
      Random random = new Random(23589735L);

      for (int numberOfVariables = 1000; numberOfVariables <= 33000; numberOfVariables += 1000)
      {
         ArrayList<JointHolder> sendJointHolders = new ArrayList<>();
         ArrayList<JointState> receiveJointStates = new ArrayList<>();

         RigidBodyBasics elevator = new RigidBody("elevator", ReferenceFrame.getWorldFrame());

         int numberOfJoints = random.nextInt(4000);
         for (int j = 0; j < numberOfJoints; j++)
         {
            OneDoFJointBasics sendJoint = new RevoluteJoint("Joint" + j, elevator, new Vector3D(1, 0, 0));
            sendJoint.setQ(random.nextDouble() * Math.PI * 2.0);
            sendJoint.setQd(random.nextDouble() * Math.PI * 2.0);
            sendJointHolders.add(new OneDoFJointHolder(sendJoint));
            receiveJointStates.add(new OneDoFState("Joint" + j));
         }

         int numberOfJointStates = RegistrySendBufferBuilder.getNumberOfJointStates(sendJointHolders);

         // Transmit data
         CustomLogDataPublisherType publisherType = new CustomLogDataPublisherType(numberOfVariables, numberOfJointStates);
         SerializedPayload payload = new SerializedPayload(publisherType.getMaximumTypeSize());

         long timestamp = random.nextLong();
         long uid = random.nextLong();

         YoRegistry sendRegistry = new YoRegistry("sendRegistry");
         YoRegistry receiveRegistry = new YoRegistry("receiveRegistry");

         // Fill the registry that is going to send data with YoVariables, this way it actually has something to send and isn't empty
         for (int v = 0; v < numberOfVariables; v++)
         {
            YoLong newLong = new YoLong("var" + v, sendRegistry);
            newLong.set(random.nextLong());
         }

         // Receive data
         CustomLogDataSubscriberType subscriberType = new CustomLogDataSubscriberType(calculateMaximumNumberOfVariables(numberOfVariables, numberOfJointStates),
                                                                                      numberOfJointStates);

         // Make copies of the YoVariables and put them int the recieveing buffer, that way when variables are sent, they have a place to be stored
         for (int v = 0; v < numberOfVariables; v++)
         {
            new YoLong("var" + v, receiveRegistry);
         }

         // Now that the data has been generated, test the buffers, update the variables and assert that the variables in both send and receive buffers match
         RegistryDecompressor registryDecompressor = new RegistryDecompressor(receiveRegistry.collectSubtreeVariables(), receiveJointStates);
         RegistrySendBuffer sendBuffer = new RegistrySendBuffer(1, sendRegistry.collectSubtreeVariables(), sendJointHolders);
         RegistryReceiveBuffer receiveBuffer = new RegistryReceiveBuffer(sendBuffer.getTimestamp());

         payload.getData().clear();

         // This will calculate the time taken to update the buffer with the new values for the variables
         long start = System.nanoTime();
         sendBuffer.updateBufferFromVariables(timestamp, uid, numberOfVariables);
         System.out.println("Time taken to update when variables and joint states total to: " + (numberOfVariables + numberOfJointStates) + " : Time: "
                            + Conversions.nanosecondsToSeconds(System.nanoTime() - start));

         publisherType.serialize(sendBuffer, payload);
         subscriberType.deserialize(payload, receiveBuffer);

         registryDecompressor.decompressSegment(receiveBuffer, 0);

         // Double check that all the variables that were transmitted are the same, otherwise through error
         List<YoVariable> sendVariables = sendRegistry.collectSubtreeVariables();
         List<YoVariable> receiveVariables = receiveRegistry.collectSubtreeVariables();

         assertEquals(sendVariables.size(), receiveVariables.size());
         for (int t = 0; t < sendVariables.size(); t++)
         {
            assertEquals(sendVariables.get(t).getValueAsLongBits(), receiveVariables.get(t).getValueAsLongBits());
         }
      }
   }
}
