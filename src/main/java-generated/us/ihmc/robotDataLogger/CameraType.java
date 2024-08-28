package us.ihmc.robotDataLogger;

/**
* 
* Definition of the enum "CameraType" defined in CameraSettings.idl. 
*
* This file was automatically generated from CameraSettings.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit CameraSettings.idl instead.
*
*/
import us.ihmc.idl.IDLTools;

public enum CameraType
{
         CAPTURE_CARD_MAGEWELL,
      
         CAPTURE_CARD,
      
         NETWORK_STREAM,
      
   ;
   public static CameraType[] values = values();

   public boolean epsilonEquals(CameraType other, double epsilon)
   {
      return IDLTools.epsilonEqualsEnum(this, other, epsilon);
   }
}