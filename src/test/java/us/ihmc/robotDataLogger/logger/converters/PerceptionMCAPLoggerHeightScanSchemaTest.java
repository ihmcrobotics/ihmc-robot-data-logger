package us.ihmc.robotDataLogger.logger.converters;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.MsgContext;
import us.ihmc.jros2.parser.MsgParser;
import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;
import us.ihmc.jros2.parser.msgdeps.MsgDepsContext;
import us.ihmc.jros2.parser.msgdeps.MsgDepsParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This test walks the real {@code .msg} files on disk (following whatever types {@code HeightScanMessage} actually
 * references, transitively - not a hardcoded list) and compares their fields against what's parsed back out of
 * {@code HEIGHT_SCAN_SCHEMA}, using the same {@code jros2-parser} library the MCAP registry format is modeled on.
 * Any field added/removed/changed, or any dependency added/removed, in the real {@code .msg} tree without a
 * matching update to {@code HEIGHT_SCAN_SCHEMA} fails this test.
 */
class PerceptionMCAPLoggerHeightScanSchemaTest
{
   private static final String ROOT_PACKAGE_RESOURCE_NAME = "perception_msgs/HeightScanMessage";
   /** Directories under the module root that {@code generateMessages} (see build.gradle.kts) also scans for .msg files. */
   private static final List<String> PACKAGE_ROOTS = List.of("perception_msgs", "geometry_msgs", "logger_msgs");

   @Test
   void heightScanSchemaMatchesMsgSources() throws IOException, InterfaceFieldParsingException
   {
      MsgContext expectedRoot = parseMsgFile(ROOT_PACKAGE_RESOURCE_NAME);
      Map<String, MsgContext> expectedDependencies = new LinkedHashMap<>();
      collectDependencies(expectedRoot, expectedDependencies);

      MsgDepsContext actual = MsgDepsParser.parseMsgDeps(PerceptionMCAPLogger.HEIGHT_SCAN_SCHEMA, ROOT_PACKAGE_RESOURCE_NAME);

      assertFieldsMatch(expectedRoot, actual, ROOT_PACKAGE_RESOURCE_NAME);

      assertEquals(expectedDependencies.keySet(),
                   actual.getDependencies().keySet(),
                   "HEIGHT_SCAN_SCHEMA's MSG: blocks no longer match the set of types HeightScanMessage.msg actually depends on - "
                   + "update PerceptionMCAPLogger.HEIGHT_SCAN_SCHEMA");

      for (Map.Entry<String, MsgContext> entry : expectedDependencies.entrySet())
      {
         assertFieldsMatch(entry.getValue(), actual.getDependencies().get(entry.getKey()), entry.getKey());
      }
   }

   /** Recursively resolves every non-builtin field type reachable from {@code context}, reading each from its real .msg file. */
   private static void collectDependencies(MsgContext context, Map<String, MsgContext> collectedDependencies)
         throws IOException, InterfaceFieldParsingException
   {
      for (InterfaceField field : context.getFieldList())
      {
         if (field.isBuiltinType())
            continue;

         String packageResourceName = field.getType().contains("/") ? field.getType() : context.getPackageName() + "/" + field.getType();
         if (collectedDependencies.containsKey(packageResourceName))
            continue;

         MsgContext dependency = parseMsgFile(packageResourceName);
         collectedDependencies.put(packageResourceName, dependency);
         collectDependencies(dependency, collectedDependencies);
      }
   }

   private static MsgContext parseMsgFile(String packageResourceName) throws IOException, InterfaceFieldParsingException
   {
      String typeName = packageResourceName.substring(packageResourceName.lastIndexOf('/') + 1);
      Path msgFile = findMsgFile(typeName);
      return MsgParser.parseMsg(Files.readString(msgFile), packageResourceName);
   }

   /**
    * ihmc-build runs this test's task from {@code ihmc-robot-data-logger/src/test}, not the module root where
    * {@code perception_msgs/}, {@code geometry_msgs/} etc. actually live - so walk upward from the working directory
    * to find the first ancestor that has them, rather than hardcoding a relative depth that ihmc-build could change.
    */
   private static Path findMsgFile(String typeName) throws IOException
   {
      Path directory = Path.of("").toAbsolutePath();
      for (int depth = 0; depth < 5 && directory != null; depth++, directory = directory.getParent())
      {
         for (String packageRoot : PACKAGE_ROOTS)
         {
            Path msgFile = directory.resolve(packageRoot).resolve("msg").resolve(typeName + ".msg");
            if (Files.exists(msgFile))
               return msgFile;
         }
      }

      throw new IOException("Could not find a .msg source file for '" + typeName + "' under any of " + PACKAGE_ROOTS
                             + "/msg/, searched from " + Path.of("").toAbsolutePath() + " upward");
   }

   private static void assertFieldsMatch(MsgContext expected, MsgContext actual, String label)
   {
      assertNotNull(actual, "HEIGHT_SCAN_SCHEMA is missing a MSG: block for " + label + " - it exists in the .msg sources but not in the embedded schema");

      assertEquals(fieldSignatures(expected), fieldSignatures(actual), "HEIGHT_SCAN_SCHEMA's fields for " + label
                                                                        + " no longer match " + label
                                                                        + ".msg - update PerceptionMCAPLogger.HEIGHT_SCAN_SCHEMA");
   }

   /** A per-field string capturing everything that matters for the MCAP schema, normalized so unqualified same-package types
    *  (as written in a .msg source) compare equal to their fully-qualified form (as HEIGHT_SCAN_SCHEMA writes the root message's
    *  own fields). */
   private static List<String> fieldSignatures(MsgContext context)
   {
      List<String> signatures = new ArrayList<>();
      for (InterfaceField field : context.getFieldList())
      {
         String type = field.isBuiltinType() || field.getType().contains("/") ? field.getType() : context.getPackageName() + "/" + field.getType();
         String arrayPart = "";
         if (field.isArray())
            arrayPart = field.isUnbounded() ? "[]" : field.isUpperBounded() ? "[<=" + field.getLength() + "]" : "[" + field.getLength() + "]";
         String constantPart = field.getConstantValue() != null ? " = " + field.getConstantValue() : "";
         signatures.add(type + arrayPart + " " + field.getName() + constantPart);
      }
      return signatures;
   }
}
