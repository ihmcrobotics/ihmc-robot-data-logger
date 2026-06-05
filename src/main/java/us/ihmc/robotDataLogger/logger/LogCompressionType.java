package us.ihmc.robotDataLogger.logger;

public enum LogCompressionType
{
   NONE, SNAPPY, ZSTD;

   public static LogCompressionType fromString(String value)
   {
      return switch (value.trim().toLowerCase())
      {
         case "", "none" -> NONE;
         case "snappy" -> SNAPPY;
         case "zstd" -> ZSTD;
         default -> throw new IllegalArgumentException("Unsupported compression type: " + value);
      };
   }
}
