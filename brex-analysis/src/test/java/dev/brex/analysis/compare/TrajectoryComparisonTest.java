package dev.brex.analysis.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.TestRuns;
import dev.brex.core.geometry.Pose;
import dev.brex.core.trajectory.Trajectory;
import org.junit.jupiter.api.Test;

class TrajectoryComparisonTest {

    @Test
    void identicalTrajectoriesHaveNoError() {
        Trajectory path = TestRuns.straight(5, 0);

        TrajectoryComparison comparison = TrajectoryComparison.compare(path, path);

        assertEquals(0, comparison.maxPositionError(), 1e-9);
        assertEquals(0, comparison.maxPathDeviation(), 1e-9);
        assertEquals(0, comparison.finalTimingOffset(), 1e-9);
    }

    @Test
    void lateralOffsetShowsAsPathDeviation() {
        TrajectoryComparison comparison = TrajectoryComparison.compare(TestRuns.straight(5, 0),
                TestRuns.straight(5, 0.03));

        assertEquals(0.03, comparison.meanPathDeviation(), 1e-9);
        assertEquals(0.03, comparison.maxPositionError(), 1e-9);
    }

    @Test
    void slowerRunOnSamePathHasTimingOffsetButNoPathDeviation() {
        TrajectoryComparison comparison = TrajectoryComparison.compare(TestRuns.straight(5, 0),
                TestRuns.straight(6, 0));

        assertEquals(0, comparison.maxPathDeviation(), 1e-9);
        assertEquals(1.0, comparison.finalTimingOffset(), 1e-6);
        assertTrue(comparison.maxPositionError() > 0.3, "time-aligned error reflects the lag");
        assertTrue(comparison.meanSpeedDifference() > 0);
    }

    @Test
    void matchesSelfCrossingPathsInOrder() {
        // Out to x=1 and back to the start, twice. Positions repeat, so a naive nearest-point
        // search would match the second lap to the first.
        Trajectory laps = TestRuns.sampled(8, t -> {
            double phase = (t % 4) / 4;
            double x = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
            return Pose.of(x, 0, phase < 0.5 ? 0 : Math.PI);
        });

        TrajectoryComparison comparison = TrajectoryComparison.compare(laps, laps);

        for (GhostSample sample : comparison.samples()) {
            assertEquals(sample.time(), sample.matchedBaselineTime(), 0.05, "at t=" + sample.time());
        }
    }

    @Test
    void turnInPlaceIsMatchedByHeading() {
        Trajectory spin = TestRuns.sampled(2, t -> Pose.of(0, 0, t));

        TrajectoryComparison comparison = TrajectoryComparison.compare(spin, spin);

        assertEquals(0, comparison.finalTimingOffset(), 0.05);
        assertEquals(0, comparison.maxHeadingError(), 1e-6);
    }

    @Test
    void emptyTrajectoriesProduceEmptyComparison() {
        TrajectoryComparison comparison = TrajectoryComparison.compare(Trajectory.empty(), TestRuns.straight(1, 0));

        assertTrue(comparison.isEmpty());
        assertTrue(Double.isNaN(comparison.maxPathDeviation()));
    }
}
