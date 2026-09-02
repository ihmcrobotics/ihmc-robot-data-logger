package us.ihmc.robotDataLogger.yoGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The flattened field data for a tree of YoGraphics, one {@link YoGraphicFieldsData} per node.
 * This is the logger's public-API representation of "some client's YoGraphic definitions" - it
 * knows nothing about any particular YoGraphic definition library, only the wire format
 * ({@code logger_msgs.SCS2YoGraphicDefinitionMessage}).
 */
public class YoGraphicsData
{
   private final List<YoGraphicFieldsData> yoGraphicFieldsDataList = new ArrayList<>();

   public YoGraphicsData()
   {
   }

   public YoGraphicsData(List<YoGraphicFieldsData> yoGraphicFieldsDataList)
   {
      this.yoGraphicFieldsDataList.addAll(yoGraphicFieldsDataList);
   }

   public void add(YoGraphicFieldsData yoGraphicFields)
   {
      yoGraphicFieldsDataList.add(yoGraphicFields);
   }

   public List<YoGraphicFieldsData> getYoGraphicFieldsDataList()
   {
      return Collections.unmodifiableList(yoGraphicFieldsDataList);
   }

   public int size()
   {
      return yoGraphicFieldsDataList.size();
   }

   public boolean isEmpty()
   {
      return yoGraphicFieldsDataList.isEmpty();
   }
}
