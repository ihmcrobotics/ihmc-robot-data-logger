package us.ihmc.robotDataLogger.dataBuffers;

import java.lang.reflect.Array;
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
    * The variables split by type, paired with where each one sits in the full variable list, {@code indices[k]} is the position
    * of {@code variables[k]} is that list.
    * <p>
    * That one ordering is used by three things: the registry defines it, {@code cachedVariableValues} is indexed by it,
    * and an incoming packet carries its values in it.
    * </p>
    * <p>
    * A packet does not necessarily carry the whole list. It carries one contiguous run of it - the segment
    * {@code [registryOffset, registryOffset + numberOfVariables)}. Because each partition holds its indices in
    * ascending order, the part of a partition covering that segment is contiguous too, so
    * {@link #firstEntryAtOrAfter} can binary search for where it starts and the loop just walks forward until it
    * passes the end.
    * </p>
    * <p>
    * The split exists so {@link #updateVariables} can run one loop per type. Applying values through
    * {@link YoVariable#setValueFromLongBits(long, boolean)} over the mixed array makes that call site megamorphic -
    * five receiver types - and HotSpot then refuses to inline or devirtualize it, and cannot constant-fold the
    * {@code false} literal into the callee. A loop that only ever sees one concrete type gets all of that back.
    * Real timing tests show the separate loops are faster. Compare with the benchmark tests for this class
    * </p>
    */
   private record TypePartition<T>(T[] variables, int[] indices)
   {
      /**
       * Index into this partition of the first variable whose position in the full variable list is at or after
       * {@code target}, or {@code indices.length} if there is none. {@code indices} is ascending, so binary search
       * works.
       */
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

   private final TypePartition<YoDouble> yoDoublesPartition;
   private final TypePartition<YoBoolean> yoBooleansPartition;
   private final TypePartition<YoInteger> yoIntegersPartition;
   private final TypePartition<YoLong> yoLongsPartition;
   private final TypePartition<YoEnum> yoEnumsPartition;
   /** Anything that is not one of the five known concrete types, applied through the original virtual dispatch. */
   private final TypePartition<YoVariable> yoOthersPartition;

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
      this.yoDoublesPartition = partition(variables, YoDouble.class);
      this.yoBooleansPartition = partition(variables, YoBoolean.class);
      this.yoIntegersPartition = partition(variables, YoInteger.class);
      this.yoLongsPartition = partition(variables, YoLong.class);
      this.yoEnumsPartition = partition(variables, YoEnum.class);
      this.yoOthersPartition = partitionRemainder(variables);

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
    * Collects every variable assignable to {@code type} - {@code type} itself or any subclass of it.
    * <p>
    * Subclasses have to count, which is why this tests {@code isInstance} rather than comparing classes outright:
    * {@code ihmc-yovariables} implements parameters as private subclasses of the plain variable types, so a
    * {@code DoubleParameter} registers a {@code YoDoubleParameter extends YoDouble}. Those belong with the doubles.
    * They inherit {@code set} unchanged, so the loop's call site still resolves to a single method, whereas an
    * exact-class test would strand every parameter in {@link #yoOthersPartition} on the virtual path this class exists to avoid.
    * </p>
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

      // Create the backing array so things are fast when iterating through
      int[] array = new int[indices.size()];
      for (int i = 0; i < array.length; i++)
      {
         array[i] = indices.get(i);
      }

      T[] matchedArray = (T[]) Array.newInstance(type, matched.size());
      return new TypePartition<>(matched.toArray(matchedArray), array);
   }

   /** Everything none of the typed partitions claimed. Expected to be empty; kept so no variable is dropped. */
   private static TypePartition<YoVariable> partitionRemainder(List<YoVariable> variables)
   {
      List<YoVariable> matched = new ArrayList<>();
      List<Integer> indices = new ArrayList<>();

      for (int i = 0; i < variables.size(); i++)
      {
         YoVariable variable = variables.get(i);
         // As the comment says we expect this to be empty, but have it if any new types are created and this isn't updated
         if (!(variable instanceof YoDouble || variable instanceof YoBoolean || variable instanceof YoInteger || variable instanceof YoLong
               || variable instanceof YoEnum))
         {
            matched.add(variable);
            indices.add(i);
         }
      }

      // Create the backing array so things are fast when iterating through
      int[] array = new int[indices.size()];
      for (int i = 0; i < array.length; i++)
      {
         array[i] = indices.get(i);
      }

      return new TypePartition<>(matched.toArray(new YoVariable[0]), array);
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
    * The multiple loops go through each type by itself, it calls exactly what {@link YoDouble#setValueFromLongBits(long, boolean)}
    * would have called. {@code setValueFromLongBits} is abstract with five implementations, and a registry mixes all of them,
    * so the virtual call site here is megamorphic: HotSpot cannot inline it, cannot devirtualize it,
    * and cannot even constant-fold the {@code false} literal into the callee, so each of these calls costs a vtable dispatch plus,
    * because the receiver type is unpredictable, a likely branch misprediction.
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
      // Each type's loop jumps to its own slots rather than walking the segment in order, so reads are absolute:
      // base + (index - registryOffset) turns a variable's position in the full list into a buffer index. Absolute
      // reads leave the cursor alone, so the position is advanced by hand at the end - nothing depends on that today
      // (decompressSegment clears the buffer first), but it keeps this a drop-in for the sequential reads it replaced.
      int base = longData.position();
      int endIndex = registryOffset + numberOfVariables;

      for (int k = yoDoublesPartition.firstEntryAtOrAfter(registryOffset); k < yoDoublesPartition.indices().length; k++)
      {
         int index = yoDoublesPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoDoublesPartition.variables()[k].set(Double.longBitsToDouble(value), false);
         cachedVariableValues[index] = value;
      }

      for (int k = yoBooleansPartition.firstEntryAtOrAfter(registryOffset); k < yoBooleansPartition.indices().length; k++)
      {
         int index = yoBooleansPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoBooleansPartition.variables()[k].set(value == 1, false);
         cachedVariableValues[index] = value;
      }

      for (int k = yoIntegersPartition.firstEntryAtOrAfter(registryOffset); k < yoIntegersPartition.indices().length; k++)
      {
         int index = yoIntegersPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoIntegersPartition.variables()[k].set((int) value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = yoLongsPartition.firstEntryAtOrAfter(registryOffset); k < yoLongsPartition.indices().length; k++)
      {
         int index = yoLongsPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoLongsPartition.variables()[k].set(value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = yoEnumsPartition.firstEntryAtOrAfter(registryOffset); k < yoEnumsPartition.indices().length; k++)
      {
         int index = yoEnumsPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoEnumsPartition.variables()[k].set((int) value, false);
         cachedVariableValues[index] = value;
      }

      for (int k = yoOthersPartition.firstEntryAtOrAfter(registryOffset); k < yoOthersPartition.indices().length; k++)
      {
         int index = yoOthersPartition.indices()[k];
         if (index >= endIndex)
            break;
         long value = longData.get(base + index - registryOffset);
         yoOthersPartition.variables()[k].setValueFromLongBits(value, false);
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
