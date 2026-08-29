package us.ihmc.robotDataLogger.yoGraphics;

import java.util.ArrayList;

/**
 * The flattened field-name/field-value pairs for a single YoGraphic node. A full YoGraphic tree
 * is represented as a {@code List<YoGraphicFieldsData>}, one entry per node, in the order needed
 * to reconstruct the tree shape (root first, depth-first).
 */
public class YoGraphicFieldsData extends ArrayList<YoGraphicFieldData>
{
   private static final long serialVersionUID = 1L;

   public void addField(String fieldName, String fieldValue)
   {
      add(new YoGraphicFieldData(fieldName, fieldValue));
   }
}
