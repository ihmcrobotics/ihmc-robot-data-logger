package us.ihmc.robotDataLogger.yoGraphics;

/**
 * A single named field of a YoGraphic, serialized as a String. Mirrors the wire format
 * ({@code logger_msgs.SCS2YoGraphicDefinitionMessage}), which is the logger's only contract with
 * whatever YoGraphic definition library the client/reader is using.
 */
public class YoGraphicFieldData
{
   private final String fieldName;
   private final String fieldValue;

   public YoGraphicFieldData(String fieldName, String fieldValue)
   {
      this.fieldName = fieldName;
      this.fieldValue = fieldValue;
   }

   public String getFieldName()
   {
      return fieldName;
   }

   public String getFieldValue()
   {
      return fieldValue;
   }
}
