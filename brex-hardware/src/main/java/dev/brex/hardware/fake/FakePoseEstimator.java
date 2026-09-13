package dev.brex.hardware.fake;

import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.hardware.PoseEstimator;

/** A localizer whose estimate is set by the test. */
public final class FakePoseEstimator implements PoseEstimator {

    private Pose pose = Pose.ORIGIN;
    private Velocity velocity;

    @Override
    public Pose pose() {
        return pose;
    }

    @Override
    public Velocity velocity() {
        return velocity;
    }

    public FakePoseEstimator set(Pose pose) {
        this.pose = pose;
        return this;
    }

    public FakePoseEstimator set(Pose pose, Velocity velocity) {
        this.pose = pose;
        this.velocity = velocity;
        return this;
    }
}
