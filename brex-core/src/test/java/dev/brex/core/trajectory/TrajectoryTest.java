package dev.brex.core.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import org.junit.jupiter.api.Test;

class TrajectoryTest {

    private static final double EPS = 1e-9;

    private static Trajectory straightLine() {
        return new Trajectory.Builder()
                .add(0.0, Pose.of(0, 0, 0))
                .add(1.0, Pose.of(1, 0, 0))
                .add(2.0, Pose.of(2, 0, Math.PI / 2))
                .build();
    }

    @Test
    void interpolatesPoseBetweenSamples() {
        Pose pose = straightLine().poseAt(1.5);

        assertEquals(1.5, pose.x(), EPS);
        assertEquals(Math.PI / 4, pose.heading(), EPS);
    }

    @Test
    void clampsPoseOutsideRecordedRange() {
        Trajectory trajectory = straightLine();

        assertEquals(Pose.of(0, 0, 0), trajectory.poseAt(-5));
        assertEquals(trajectory.endPose(), trajectory.poseAt(100));
    }

    @Test
    void estimatesVelocityWhenNotRecorded() {
        Trajectory trajectory = straightLine();

        assertFalse(trajectory.hasRecordedVelocity());
        assertEquals(1.0, trajectory.velocity(1).vx(), EPS);
    }

    @Test
    void prefersRecordedVelocity() {
        Trajectory trajectory = new Trajectory.Builder()
                .add(0, Pose.ORIGIN, Velocity.of(0.3, 0, 0))
                .add(1, Pose.of(1, 0, 0), Velocity.of(0.7, 0, 0))
                .build();

        assertTrue(trajectory.hasRecordedVelocity());
        assertEquals(0.7, trajectory.velocityAt(1.2).vx(), EPS);
    }

    @Test
    void computesPathLengthAndDuration() {
        Trajectory trajectory = straightLine();

        assertEquals(2.0, trajectory.pathLength(), EPS);
        assertEquals(0.0, trajectory.startTime());
        assertEquals(2.0, trajectory.endTime());
    }

    @Test
    void slicesByTime() {
        Trajectory slice = straightLine().slice(0.5, 2.0);

        assertEquals(2, slice.size());
        assertEquals(1.0, slice.time(0));
    }

    @Test
    void emptyTrajectoryExplainsItself() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Trajectory.empty().endPose());

        assertTrue(error.getMessage().contains("no poses were recorded"));
    }

    @Test
    void fromColumnsRejectsMismatchedLengths() {
        assertThrows(IllegalArgumentException.class, () -> Trajectory.fromColumns(new double[] {0, 1},
                new double[] {0}, new double[] {0, 1}, new double[] {0, 1}, null, null, null));
    }
}
