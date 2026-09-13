package dev.brex.hardware;

import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;

/**
 * The robot's localizer: odometry, a path follower's pose estimate, or a vision fusion. B-rex only
 * reads it; your code keeps calling the localizer's own update method.
 */
public interface PoseEstimator {

    /** The current field-relative pose estimate. */
    Pose pose();

    /** The current velocity estimate, or null when the localizer does not provide one. */
    Velocity velocity();
}
