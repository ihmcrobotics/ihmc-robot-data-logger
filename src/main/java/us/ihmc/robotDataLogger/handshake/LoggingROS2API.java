package us.ihmc.robotDataLogger.handshake;

import perception_msgs.HeightScanMessage;
import us.ihmc.jros2.ROS2Topic;

/**
 * Canonical ROS2 topic definitions for subscribers in this repo, and scs2's live-session).
 */
public class LoggingROS2API
{
   public static final ROS2Topic<HeightScanMessage> HEIGHT_SCAN = new ROS2Topic<>("/height_scan/height_scan_message", HeightScanMessage.class);
}
