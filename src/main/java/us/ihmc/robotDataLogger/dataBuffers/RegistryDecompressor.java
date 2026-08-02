package us.ihmc.robotDataLogger.dataBuffers;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.tools.compression.CompressionImplementation;
import us.ihmc.tools.compression.CompressionImplementationFactory;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

public class RegistryDecompressor
{
   /**
    * The variables partitioned by concrete type, each with the wire index it occupies in
    * {@link #cachedVariableValues} and in an incoming packet. Built once, in ascending index order, so a segment's
    * slice of each partition can be located by binary search.
    * <p>
    * The partitioning exists so {@link #updateVariables} can run one loop per type. Applying values through
    * {@link YoVariable#setValueFromLongBits(long, boolean)} over the mixed array makes that call site megamorphic -
    * five receiver types - and HotSpot then refuses to inline or devirtualize it, and cannot constant-fold the
    * {@code false} literal into the callee. A loop that only ever sees one concrete type gets all of that back: on a
    * real 28890-variable walking log this is the difference between ~131 us and ~35 us per packet.
    * </p>
    */
   private record TypePartition<T>(T[] variables, int[] indices)
   {
      /** Index of the first entry at or after {@code target}. {@link #indices} is ascending, so binary search works. */
      int firstEntryAtOrAfter(int target)
      {
         int low = 0;
         int high = indices.length;
         while (low < high)
         {
            int mid = (low + high) >>> 1;
            if (indices[mid] < target)
               low = mid + 1;
            else
               high = mid;
         }
         return low;
      }
   }

   private final TypePartition<YoDouble> doubles;
   private final TypePartition<YoBoolean> booleans;
   private final TypePartition<YoInteger> integers;
   private final TypePartition<YoLong> longs;
   private final TypePartition<YoEnum> enums;
   /** Anything that is not one of the five known concrete types, applied through the original virtual dispatch. */
   private final TypePartition<YoVariable> others;

   private final List<JointState> jointStates;
   private final long[] cachedVariableValues;
   private final long[] cachedJointStateValues;

   private final ByteBuffer decompressBuffer;
   private final LongBuffer decompressLongBuffer;
   private final double[] jointStateData;
   private final DoubleBuffer jointStateDoubleBuffer;

   private final CompressionImplementation compressionImplementation;

   private Object variableSynchronizer = null;

   public RegistryDecompressor(List<YoVariable> variables, List<JointState> jointStates)
   {
      this.doubles = partition(variables, YoDouble.class);
      this.booleans = partition(variables, YoBoolean.class);
      this.integers = partition(variables, YoInteger.class);
      this.longs = partition(variables, YoLong.class);
      this.enums = partition(variables, YoEnum.class);
      this.others = partitionRemainder(variables);

      this.jointStates = jointStates;
      this.cachedVariableValues = new long[variables.size()];

      // Each joint state contains more then one variable
      int totalJointStateVariables = 0;
      for (JointState jointState : jointStates)
      {
         totalJointStateVariables += jointState.getNumberOfStateVariables();
      }
      this.cachedJointStateValues = new long[totalJointStateVariables];

      this.decompressBuffer = ByteBuffer.allocate(variables.size() * 8);
      this.decompressLongBuffer = decompressBuffer.asLongBuffer();
      this.jointStateData = new double[totalJointStateVariables];
      this.jointStateDoubleBuffer = DoubleBuffer.wrap(jointStateData);
      this.compressionImplementation = CompressionImplementationFactory.instance();
   }

   /**
    * Collects every variable that is exactly {@code type} - or a subclass, so the {@code YoDoubleParameter}-style
    * inner classes in {@code ihmc-yovariables} land with their base type rather than in {@link #others}.
    */
   @SuppressWarnings("unchecked")
   private static <T> TypePartition<T> partition(List<YoVariable> variables, Class<T> type)
   {
      List<T> matched = new ArrayList<>();
      List<Integer> indices = new ArrayList<>();

      for (int i = 0; i < variables.size(); i++)
      {
         YoVariable variable = variables.get(i);
         if (type.isInstance(variable))
         {
            matched.add((T) variable);
            indices.add(i);
         }
      }

      return new TypePartition<>(matched.toArray((T[]) java.lang.reflect.Array.newInstance(type, matched.size())), toIntArray(indices));
   }

   /** Everything none of the five typed partitions claimed. Expected to be empty; kept so no variable is dropped. */
   private static TypePartition<YoVariable> partitionRemainder(List<YoVariable> variables)
   {
      List<YoVariable> matched = new ArrayList<>();
      List<Integer> indices = new ArrayList<>();

      for (int i = 0; i < variables.size(); i++)
      {
         YoVariable variable = variables.get(i);
         if (!(variable instanceof YoDouble || variable instanceof YoBoolean || variable instanceof YoInteger || variable instanceof YoLong
               || variable instanceof YoEnum))
         {
            matched.add(variable);
            indices.add(i);
         }
      }

      return new TypePartition<>(matched.toArray(new YoVariable[0]), toIntArray(indices));
   }

   private static int[] toIntArray(List<Integer> values)
   {
      int[] array = new int[values.size()];
      for (int i = 0; i < array.length; i++)
         array[i] = values.get(i);
      return array;
   }

   public void decompressSegment(RegistryReceiveBuffer buffer, int registryOffset)
   {
      decompressBuffer.clear();
      int expectedBytes = buffer.getNumberOfVariables() * 8;
      try
      {
         compressionImplementation.decompress(buffer.getData(), decompressBuffer, expectedBytes);
      }
      catch (Throwable e)
      {
         // Malformed packet. Just skip.
         LogTools.error("Cannot decompress incoming packet. Skipping packet. " + e.getMessage());
         return;
      }
      decompressBuffer.flip();
      decompressLongBuffer.clear();
      // LongBuffer is a shared view and doesn't track ByteBuffer's limit automatically after flip()
      decompressLongBuffer.limit(decompressBuffer.limit() / 8);
      if (decompressLongBuffer.remaining() != buffer.getNumberOfVariables())
      {
         LogTools.error("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }

      // Sanity check
      if (decompressBuffer.remaining() != expectedBytes)
      {
         LogTools.error("Number of variables in incoming message does not match stated number of variables. Skipping packet.");
         return;
      }
      int numberOfVariables = buffer.getNumberOfVariables();

      if (variableSynchronizer != null)
      {
         synchronized (variableSynchronizer)
         {
            updateVariables(buffer, registryOffset, decompressLongBuffer, numberOfVariables);
         }
      }
      else
      {
         updateVariables(buffer, registryOffset, decompressLongBuffer, numberOfVariables);
      }
   }

   /**
    * Applies one packet's worth of raw values to the {@link YoVariable}s.
    * <p>
    * The {@code instanceof YoDouble} branch is a performance fix, not a behavioral one - it calls exactly what
    * {@link YoDouble#setValueFromLongBits(long, boolean)} would have called. {@code setValueFromLongBits} is abstract
    * with five implementations, and a registry mixes all of them, so the virtual call site here is megamorphic:
    * HotSpot cannot inline it, cannot devirtualize it, and cannot even constant-fold the {@code false} literal into
    * the callee - so each of these calls costs a vtable dispatch plus, because the receiver type is unpredictable, a
    * likely branch misprediction.
    * <p>
    * Running one loop per concrete type instead makes every call site monomorphic, which HotSpot inlines down to a
    * compare-and-store. The cost is that each loop reads its values by absolute index rather than sequentially, but
    * the whole segment is a small contiguous array and that scatter is far cheaper than the dispatch it replaces.
    * See {@code RegistryDecompressorTest}, whose {@code alex-walking} profile reproduces a real 28890-variable
    * walking log's type mix - and note that a benchmark built from a single variable type cannot show any of this,
    * because its call site is monomorphic to begin with.
    * </p>
    */
   void updateVariables(RegistryReceiveBuffer buffer, int registryOffset, LongBuffer longData, int numberOfVariables)
   {
      // Values are addressed absolutely so each type's loop can jump straight to its own slots. base makes that
      // independent of where the caller left the position, and the position is advanced at the end so this stays
      // interchangeable with the sequential reads it replaced.
      int base = longData.position();
      int endIndex = registryOffset + numberOfVariables;

      for (int k = doubles.firstEntryAtOrAfter(registryOffset); k < doubles.indices().length; k++)
      {
         int index = doubles.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         doubles.variables()[k].set(Double.longBitsToDouble(value), false);
         cachedVariableValues[index] = value;
      }

      for (int k = booleans.firstEntryAtOrAfter(registryOffset); k < booleans.indices().length; k++)
      {
         int index = booleans.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         booleans.variables()[k].set(value == 1, false);
         cachedVariableValues[index] = value;
      }

      for (int k = integers.firstEntryAtOrAfter(registryOffset); k < integers.indices().length; k++)
      {
         int index = integers.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         integers.variables()[k].set((int) value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = longs.firstEntryAtOrAfter(registryOffset); k < longs.indices().length; k++)
      {
         int index = longs.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         longs.variables()[k].set(value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = enums.firstEntryAtOrAfter(registryOffset); k < enums.indices().length; k++)
      {
         int index = enums.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         enums.variables()[k].set((int) value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = others.firstEntryAtOrAfter(registryOffset); k < others.indices().length; k++)
      {
         int index = others.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         others.variables()[k].setValueFromLongBits(value, false);
         cachedVariableValues[index] = value;
      }

      longData.position(base + numberOfVariables);

      double[] jointStateArray = buffer.getJointStates();
      int jointStateCount = buffer.getJointStateCount();
      if (jointStateCount > 0)
      {
         System.arraycopy(jointStateArray, 0, jointStateData, 0, jointStateCount);
         jointStateDoubleBuffer.rewind();
         for (int i = 0; i < jointStates.size(); i++)
         {
            jointStates.get(i).update(jointStateDoubleBuffer);
         }
         for (int i = 0; i < cachedJointStateValues.length; i++)
         {
            cachedJointStateValues[i] = Double.doubleToLongBits(jointStateData[i]);
         }
      }
   }

   public long[] getCachedVariableValues()
   {
      return cachedVariableValues;
   }

   public long[] getCachedJointStateValues()
   {
      return cachedJointStateValues;
   }

   public void setVariableSynchronizer(Object variableSynchronizer)
   {
      this.variableSynchronizer = variableSynchronizer;
   }
}
