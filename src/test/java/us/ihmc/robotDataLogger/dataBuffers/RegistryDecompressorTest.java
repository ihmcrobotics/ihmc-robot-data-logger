package us.ihmc.robotDataLogger.dataBuffers;

import org.junit.jupiter.api.Test;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.jointState.JointState;
import us.ihmc.robotDataLogger.jointState.OneDoFState;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Times {@link RegistryDecompressor#updateVariables} across a range of variable counts and, more importantly, across
 * a range of <i>type mixes</i>.
 * <p>
 * The type mix is the point. {@code updateVariables} dispatches through
 * {@link YoVariable#setValueFromLongBits(long, boolean)}, which is abstract with five implementations. A call site
 * that sees one receiver type is inlined by HotSpot (and {@code notifyListeners = false} constant-folded away); a
 * call site that sees three or more goes megamorphic and is neither inlined nor devirtualized. A benchmark built
 * from a single type therefore measures a best case that production never hits, and would report any change aimed at
 * the megamorphic path as a regression.
 * </p>
 * <p>
 * <b>Reading the results.</b> HotSpot's inline cache and profile data for that call site are per-JVM and sticky:
 * once a mixed profile has driven it megamorphic, it stays that way for every profile measured afterwards in the
 * same run. So the sequence below does <i>not</i> give a clean monomorphic-vs-megamorphic comparison - only the
 * first profile measured gets an unpolluted call site. Use it to compare mixes under the steady-state megamorphic
 * dispatch that production actually sees. To measure one profile in isolation, run a single profile per JVM via
 * {@code registrydecompressor.benchmark.profile=<name>} (see {@link #benchmarkSetting}), which is the poor-man's
 * substitute for JMH forking.
 * </p>
 * <p>
 * That isolation matters far more for the pre-partitioning implementation than for the current one. Once each type
 * gets its own monomorphic loop, the call sites cannot be polluted by a previously measured profile, and the mixes
 * converge.
 * </p>
 */
public class RegistryDecompressorTest
{
   /** Selects a single profile to measure, for running one per JVM. Unset measures all of them in sequence. */
   private static final String PROFILE_PROPERTY = "registrydecompressor.benchmark.profile";

   /**
    * Reads a benchmark knob from a system property, falling back to the equivalent environment variable
    * ({@code registrydecompressor.benchmark.profile} becomes {@code REGISTRYDECOMPRESSOR_BENCHMARK_PROFILE}).
    * <p>
    * Both exist because neither works everywhere. Gradle forks the test JVM without passing {@code -D} through, so
    * under {@code gradlew test} the system property silently does nothing and the environment variable is the one
    * that lands. Running the test directly - from an IDE, or any plain JUnit launcher - is the reverse: {@code -D}
    * works as usual. Note that Gradle hands the test JVM the <i>daemon's</i> environment, so a variable exported
    * after the daemon started will not be seen; pass {@code --no-daemon} when setting one.
    * </p>
    */
   private static String benchmarkSetting(String key)
   {
      String property = System.getProperty(key);
      if (property != null)
         return property;
      return System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
   }

   private static int parseIntSetting(String key, int defaultValue)
   {
      String value = benchmarkSetting(key);
      return value == null ? defaultValue : Integer.parseInt(value);
   }

   /**
    * Fraction of values that differ from the previous tick. 1.0 reproduces the original benchmark, where every
    * variable changes every tick. Real streams sit well below that - constants, configuration, flags and saturated
    * states hold still - which matters for any change that tries to skip unchanged values, since
    * {@code YoVariable.set} already no-ops on an unchanged value but only after the dispatch has been paid.
    */
   private static final double CHANGE_RATE = 1.0;

   private static final int MIN_VARIABLES = 28000;
   private static final int MAX_VARIABLES = 52000;
   private static final int VARIABLE_INCREMENT = 6000;
   /** Joint-state count. Settable so the joint-state half of updateVariables can be isolated from the variable half. */
   private static final int NUMBER_OF_JOINTS = parseIntSetting("registrydecompressor.benchmark.joints", 2000);
   private static final int ITERATIONS_PER_MEASUREMENT = 1000;

   /** Stand-in for a typical logged enum - small, which is the common case. */
   private enum BenchmarkEnum
   {
      ALPHA, BRAVO, CHARLIE, DELTA
   }

   private enum VariableType
   {
      DOUBLE, INTEGER, BOOLEAN, LONG, ENUM
   }

   /**
    * A named type mix. {@code weights} are relative shares in {@link VariableType} order; they do not need to sum to
    * anything in particular.
    */
   private record TypeProfile(String name, int... weights)
   {
      /**
       * Expands the weights into a per-slot type assignment, shuffled so the types are interleaved rather than
       * grouped. Grouping would let stretches of the array behave monomorphically and quietly flatter the result;
       * on the wire the order is whatever the registry happens to be in.
       */
      VariableType[] buildTypeAssignment(int numberOfVariables, Random random)
      {
         VariableType[] types = new VariableType[numberOfVariables];
         VariableType[] allTypes = VariableType.values();

         int totalWeight = Arrays.stream(weights).sum();
         int cursor = 0;

         for (int i = 0; i < allTypes.length && cursor < numberOfVariables; i++)
         {
            int count = i == allTypes.length - 1 ? numberOfVariables - cursor : (int) ((long) numberOfVariables * weights[i] / totalWeight);
            for (int j = 0; j < count && cursor < numberOfVariables; j++)
               types[cursor++] = allTypes[i];
         }
         while (cursor < numberOfVariables) // Rounding leftovers.
            types[cursor++] = allTypes[0];

         for (int i = numberOfVariables - 1; i > 0; i--)
         {
            int j = random.nextInt(i + 1);
            VariableType swap = types[i];
            types[i] = types[j];
            types[j] = swap;
         }

         return types;
      }
   }

   //                                                                  DOUBLE  INT  BOOL  LONG  ENUM
   private static final List<TypeProfile> PROFILES = List.of(new TypeProfile("all-doubles", 1, 0, 0, 0, 0),
                                                             // Measured from a real 28890-variable Alex walking log's handshake.yaml: 84.3% double,
                                                             // 8.5% boolean, 3.3% integer, 3.1% enum, 0.8% long. Raw counts used as weights.
                                                             new TypeProfile("real-log-data", 24361, 965, 2444, 223, 897),
                                                             new TypeProfile("mixed-even", 20, 20, 20, 20, 20),
                                                             new TypeProfile("boolean-enum-majority", 20, 0, 40, 0, 40));

   /**
    * Guards the {@code instanceof YoDouble} fast path in {@link RegistryDecompressor#updateVariables}: it must apply
    * exactly what the plain {@link YoVariable#setValueFromLongBits(long, boolean)} dispatch would have, for every
    * type, and must leave {@code cachedVariableValues} holding the raw bits regardless of which path was taken.
    */
   @Test
   public void testUpdateVariablesAppliesCorrectValuesForEveryType()
   {
      Random random = new Random(24680L);
      YoRegistry registry = new YoRegistry("correctness");

      // One of every type, repeated, so both the fast path and the fallback are exercised and interleaved.
      List<YoVariable> yoVariables = new ArrayList<>();
      VariableType[] types = new VariableType[5 * 40];
      for (int i = 0; i < types.length; i++)
      {
         types[i] = VariableType.values()[i % VariableType.values().length];
         yoVariables.add(newVariable(types[i], "var" + i, registry));
      }

      RegistryDecompressor decompressor = new RegistryDecompressor(yoVariables, List.of());
      LongBuffer longBuffer = LongBuffer.allocate(types.length);

      long[] expectedValues = new long[types.length];
      for (int i = 0; i < types.length; i++)
         expectedValues[i] = nextValueFor(types[i], random, longBuffer);
      longBuffer.rewind();

      RegistryReceiveBuffer buffer = new RegistryReceiveBuffer(0);
      buffer.allocateStates(0);

      decompressor.updateVariables(buffer, 0, longBuffer, types.length);

      for (int i = 0; i < types.length; i++)
      {
         long raw = expectedValues[i];
         YoVariable variable = yoVariables.get(i);

         switch (types[i])
         {
            case DOUBLE -> assertEquals(Double.doubleToLongBits(Double.longBitsToDouble(raw)),
                                        Double.doubleToLongBits(((YoDouble) variable).getValue()),
                                        "double at " + i); // Long-bits compare so NaN payloads are checked too.
            case INTEGER -> assertEquals((int) raw, ((YoInteger) variable).getValue(), "integer at " + i);
            case BOOLEAN -> assertEquals(raw == 1, ((YoBoolean) variable).getValue(), "boolean at " + i);
            case LONG -> assertEquals(raw, ((YoLong) variable).getValue(), "long at " + i);
            case ENUM -> assertEquals((int) raw, ((YoEnum<?>) variable).getOrdinal(), "enum at " + i);
         }

         assertEquals(raw, decompressor.getCachedVariableValues()[i], "cached raw bits at " + i);
      }
   }

   /**
    * The type partitions are global but a packet carries one registry's segment, so
    * {@link RegistryDecompressor#updateVariables} has to clip each partition to {@code [registryOffset, offset + n)}.
    * This checks that a segmented update touches exactly its own slice - nothing before it, nothing after it - and
    * that values still land on the right variables once the wire slots no longer line up with the global indices.
    */
   @Test
   public void testUpdateVariablesOnlyTouchesItsOwnRegistrySegment()
   {
      Random random = new Random(13579L);
      YoRegistry registry = new YoRegistry("segmented");

      int totalVariables = 200;
      int segmentOffset = 50;
      int segmentLength = 100;

      List<YoVariable> yoVariables = new ArrayList<>();
      VariableType[] types = new VariableType[totalVariables];
      for (int i = 0; i < totalVariables; i++)
      {
         types[i] = VariableType.values()[i % VariableType.values().length];
         yoVariables.add(newVariable(types[i], "var" + i, registry));
      }

      // Every variable starts at a known "untouched" value so anything written outside the segment is detectable.
      for (YoVariable variable : yoVariables)
         variable.setValueFromLongBits(0L, false);

      RegistryDecompressor decompressor = new RegistryDecompressor(yoVariables, List.of());

      // The segment's wire data holds only its own variables, starting at slot 0 - as an incoming packet would.
      LongBuffer longBuffer = LongBuffer.allocate(segmentLength);
      long[] expectedValues = new long[segmentLength];
      for (int i = 0; i < segmentLength; i++)
         expectedValues[i] = nextValueFor(types[segmentOffset + i], random, longBuffer);
      longBuffer.rewind();

      RegistryReceiveBuffer buffer = new RegistryReceiveBuffer(0);
      buffer.allocateStates(0);

      decompressor.updateVariables(buffer, segmentOffset, longBuffer, segmentLength);

      for (int i = 0; i < totalVariables; i++)
      {
         YoVariable variable = yoVariables.get(i);
         boolean insideSegment = i >= segmentOffset && i < segmentOffset + segmentLength;
         long expectedRaw = insideSegment ? expectedValues[i - segmentOffset] : 0L;

         switch (types[i])
         {
            case DOUBLE -> assertEquals(Double.doubleToLongBits(Double.longBitsToDouble(expectedRaw)),
                                        Double.doubleToLongBits(((YoDouble) variable).getValue()),
                                        (insideSegment ? "in-segment" : "OUTSIDE-segment") + " double at " + i);
            case INTEGER -> assertEquals((int) expectedRaw, ((YoInteger) variable).getValue(), "integer at " + i);
            case BOOLEAN -> assertEquals(expectedRaw == 1, ((YoBoolean) variable).getValue(), "boolean at " + i);
            case LONG -> assertEquals(expectedRaw, ((YoLong) variable).getValue(), "long at " + i);
            case ENUM -> assertEquals((int) expectedRaw, ((YoEnum<?>) variable).getOrdinal(), "enum at " + i);
         }

         assertEquals(expectedRaw, decompressor.getCachedVariableValues()[i], "cached raw bits at " + i);
      }

      assertEquals(segmentLength, longBuffer.position(), "position must advance by the segment length, as sequential reads did");
   }

   @Test
   public void testUpdateVariablesPerformance()
   {
      String selectedProfile = benchmarkSetting(PROFILE_PROPERTY);

      List<TypeProfile> profilesToRun =
            selectedProfile == null ? PROFILES : PROFILES.stream().filter(profile -> profile.name().equals(selectedProfile)).toList();

      assertTrue(!profilesToRun.isEmpty(), "No profile named '" + selectedProfile + "'. Known: " + PROFILES.stream().map(TypeProfile::name).toList());

      if (selectedProfile == null)
         LogTools.info("Measuring all profiles in one JVM - only 'all-long' (first) sees an unpolluted call site. " + "To measure one in isolation, set "
                       + PROFILE_PROPERTY + "=<name> as a -D when running the test " + "directly, or as " + PROFILE_PROPERTY.toUpperCase(Locale.ROOT)
                                                                                                                            .replace('.', '_') + " in the "
                       + "environment when running it through Gradle.");

      List<JointState> jointStates = new ArrayList<>();
      for (int i = 0; i < NUMBER_OF_JOINTS; i++)
         jointStates.add(new OneDoFState("joint" + i));

      double[] jointArray = new double[NUMBER_OF_JOINTS * 2];
      Random jointRandom = new Random(987654L);
      for (int i = 0; i < jointArray.length; i++)
         jointArray[i] = jointRandom.nextDouble();

      RegistryReceiveBuffer buffer = new RegistryReceiveBuffer(0);
      buffer.setJointStates(jointArray);

      for (TypeProfile profile : profilesToRun)
         measureProfile(profile, jointStates, buffer);
   }

   private void measureProfile(TypeProfile profile, List<JointState> jointStates, RegistryReceiveBuffer buffer)
   {
      // Same seed per profile so the only thing varying between profiles is the type mix.
      Random random = new Random(123456L);

      VariableType[] types = profile.buildTypeAssignment(MAX_VARIABLES, random);

      YoRegistry registry = new YoRegistry("benchmark");
      List<YoVariable> yoVariables = new ArrayList<>(MAX_VARIABLES);
      for (int i = 0; i < MAX_VARIABLES; i++)
         yoVariables.add(newVariable(types[i], "var" + i, registry));

      RegistryDecompressor decompressor = new RegistryDecompressor(yoVariables, jointStates);
      LongBuffer longBuffer = LongBuffer.allocate(MAX_VARIABLES);
      long[] previousValues = new long[MAX_VARIABLES];

      for (int numberOfVariables = MIN_VARIABLES; numberOfVariables <= MAX_VARIABLES; numberOfVariables += VARIABLE_INCREMENT)
      {
         long minTimeNs = Long.MAX_VALUE;

         for (int iteration = 0; iteration < ITERATIONS_PER_MEASUREMENT; iteration++)
         {
            longBuffer.rewind();
            for (int i = 0; i < numberOfVariables; i++)
            {
               if (iteration > 0 && random.nextDouble() >= CHANGE_RATE)
                  longBuffer.put(previousValues[i]); // Unchanged since the last tick.
               else
                  previousValues[i] = nextValueFor(types[i], random, longBuffer);
            }
            longBuffer.rewind();

            long start = System.nanoTime();
            decompressor.updateVariables(buffer, 0, longBuffer, numberOfVariables);
            long end = System.nanoTime();
            minTimeNs = Math.min(minTimeNs, end - start);
         }

         LogTools.info(String.format("%-20s %6d variables, %4d joints: %8.2f us", profile.name(), numberOfVariables, NUMBER_OF_JOINTS, minTimeNs / 1000.0));

         // Not a performance bound - just a guard that the work actually happened and the loop wasn't optimized
         // away or short-circuited by an exception path.
         assertTrue(minTimeNs > 0, "updateVariables took no measurable time, which means it did not run");
      }
   }

   private static YoVariable newVariable(VariableType type, String name, YoRegistry registry)
   {
      return switch (type)
      {
         case DOUBLE -> new YoDouble(name, registry);
         case INTEGER -> new YoInteger(name, registry);
         case BOOLEAN -> new YoBoolean(name, registry);
         case LONG -> new YoLong(name, registry);
         case ENUM -> new YoEnum<>(name, registry, BenchmarkEnum.class);
      };
   }

   /**
    * Produces a raw value valid for the given type and writes it to {@code longBuffer}.
    * <p>
    * Type-aware because {@link YoEnum#set(int, boolean)} bounds-checks and throws on an ordinal outside its constant
    * range - a random long would fail immediately. The others accept any bit pattern.
    * </p>
    */
   private static long nextValueFor(VariableType type, Random random, LongBuffer longBuffer)
   {
      long value = type == VariableType.ENUM ? random.nextInt(BenchmarkEnum.values().length) : random.nextLong();
      longBuffer.put(value);
      return value;
   }
}
