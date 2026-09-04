package us.ihmc.robotDataLogger.handshake;

import perception_msgs.HeightScanMessage;
import us.ihmc.jros2.ROS2Topic;

/**
 * Canonical ROS2 topic definitions for subscribers in this repo (e.g. {@code PerceptionMCAPLogger}, and
 * scs2's live-session height-map feed). {@code perception_msgs.HeightScanMessage} (and its
 * {@code Vector2}/{@code PackedElementField}/{@code geometry_msgs} dependencies) are generated in THIS
 * repo ({@code perception_msgs/msg/}, {@code geometry_msgs/msg/}) - this is the canonical message
 * definition, so the canonical topic belongs here too, next to it, rather than as independently
 * maintained copies on each side that depends on it.
 * <p>
 * {@code ihmc-communication}'s {@code PerceptionAPI} (used by the publishing side, e.g. {@code HeightScanTerm}
 * in ihmc-closed-source-control) already depends on this repo for the message classes, so it references
 * {@link #STEPPING_HEIGHT_SCAN} directly instead of defining its own - one real definition, used everywhere.
 * This repo deliberately does NOT depend back on ihmc-communication (would be circular, since it already
 * depends on this repo) or on ihmc-interfaces-jros2 (redundant now the message lives here) - so
 * {@link #STEPPING_HEIGHT_SCAN} is built from a plain {@link ROS2Topic} with the resolved name given as a
 * literal, not via {@code HumanoidROS2Topic}-style prefix/module/suffix composition (that class lives in
 * ihmc-communication, unreachable from here).
 */
public class LoggingROS2API
{
   public static final ROS2Topic<HeightScanMessage> STEPPING_HEIGHT_SCAN =
         new ROS2Topic<>("/stepping_camera/realsense/height_scan/height_scan_message", HeightScanMessage.class);
}
