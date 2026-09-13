package dev.brex.recording;

import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.trajectory.Trajectory;

/**
 * Records the robot's pose (and optionally velocity) during a run. Call once per loop with your
 * localizer's estimate.
 *
 * <pre>{@code
 * Pose2D p = odometry.getPosition();
 * brex.trajectory.recordInches(p.getX(DistanceUnit.INCH), p.getY(DistanceUnit.INCH), p.getHeading(AngleUnit.DEGREES));
 * }</pre>
 */
public final class TrajectoryRecorder {

    private final RunRecorder recorder;
    private final Trajectory.Builder builder = new Trajectory.Builder();

    TrajectoryRecorder(RunRecorder recorder) {
        this.recorder = recorder;
    }

    public void record(Pose pose) {
        if (pose == null) {
            return;
        }
        record(pose.x(), pose.y(), pose.heading());
    }

    public void record(Pose pose, Velocity velocity) {
        if (pose == null) {
            return;
        }
        if (velocity == null) {
            record(pose);
            return;
        }
        add(pose.x(), pose.y(), pose.heading(), velocity.vx(), velocity.vy(), velocity.omega());
    }

    /** Records a pose in meters and radians. */
    public void record(double xMeters, double yMeters, double headingRadians) {
        add(xMeters, yMeters, headingRadians, Double.NaN, Double.NaN, Double.NaN);
    }

    /** Records a pose in inches and degrees. */
    public void recordInches(double xInches, double yInches, double headingDegrees) {
        record(DistanceUnit.INCHES.toMeters(xInches), DistanceUnit.INCHES.toMeters(yInches),
                Math.toRadians(headingDegrees));
    }

    private void add(double x, double y, double heading, double vx, double vy, double omega) {
        synchronized (recorder) {
            if (!recorder.acceptingSamples()) {
                return;
            }
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(heading)) {
                recorder.internalWarning("Pose with NaN coordinates was dropped; check the localizer");
                return;
            }
            if (builder.size() >= recorder.maxSamplesPerChannel()) {
                recorder.internalWarning("Trajectory reached " + recorder.maxSamplesPerChannel()
                        + " samples; later poses are dropped");
                return;
            }
            builder.add(recorder.now(), x, y, heading, vx, vy, omega);
        }
    }

    int size() {
        return builder.size();
    }

    Trajectory build() {
        return builder.build();
    }
}
