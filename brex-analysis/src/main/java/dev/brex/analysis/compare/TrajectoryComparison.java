package dev.brex.analysis.compare;

import dev.brex.analysis.Stats;
import dev.brex.core.geometry.Angles;
import dev.brex.core.geometry.Pose;
import dev.brex.core.trajectory.Trajectory;
import dev.brex.core.util.TimeSeries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares a candidate trajectory against a baseline trajectory, sample by sample.
 *
 * <h2>Path alignment</h2>
 *
 * For each candidate sample, the matching baseline point is the point on the baseline polyline
 * minimizing {@code distance + headingWeight * |headingError|}. The heading term separates
 * turn-in-place samples that share a position. The search is monotone in baseline time and
 * limited to a look-ahead window beyond the expected baseline time, so autonomous routines that
 * cross their own path (cycling to the same basket) are matched in order.
 */
public final class TrajectoryComparison {

    /**
     * @param headingWeight meters of distance equivalent to one radian of heading error when
     *     choosing the matched point
     * @param lookAheadSeconds how far beyond the expected baseline time the matcher may search
     */
    public record Options(double headingWeight, double lookAheadSeconds) {
        public static final Options DEFAULT = new Options(0.1, 1.5);
    }

    private final List<GhostSample> samples;

    private TrajectoryComparison(List<GhostSample> samples) {
        this.samples = Collections.unmodifiableList(samples);
    }

    public static TrajectoryComparison compare(Trajectory baseline, Trajectory candidate) {
        return compare(baseline, candidate, Options.DEFAULT);
    }

    public static TrajectoryComparison compare(Trajectory baseline, Trajectory candidate, Options options) {
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return new TrajectoryComparison(List.of());
        }
        double[] bt = baseline.times();
        double[] bx = baseline.xs();
        double[] by = baseline.ys();
        double[] bh = baseline.headings();

        List<GhostSample> result = new ArrayList<>(candidate.size());
        double lastMatchedTime = bt[0];
        double previousCandidateTime = candidate.time(0);
        for (int i = 0; i < candidate.size(); i++) {
            double t = candidate.time(i);
            Pose c = candidate.pose(i);
            double expected = lastMatchedTime + (t - previousCandidateTime);
            previousCandidateTime = t;

            int lo = Math.max(0, TimeSeries.indexAtOrBefore(bt, lastMatchedTime));
            int hi = Math.max(lo, TimeSeries.indexAtOrBefore(bt, expected + options.lookAheadSeconds()));
            hi = Math.min(Math.max(hi, lo + 1), bt.length - 1);

            double bestCost = Double.POSITIVE_INFINITY;
            double bestDistance = 0;
            double bestTime = bt[lo];
            Pose bestPose = baseline.pose(lo);
            int bestSegment = lo;
            for (int j = lo; j <= Math.max(lo, hi - 1); j++) {
                int k = Math.min(j + 1, bt.length - 1);
                double segX = bx[k] - bx[j];
                double segY = by[k] - by[j];
                double lengthSq = segX * segX + segY * segY;
                double u;
                if (lengthSq == 0) {
                    // Turning in place: interpolate along the segment's heading change instead.
                    double span = Angles.difference(bh[j], bh[k]);
                    u = span == 0 ? 0 : Angles.difference(bh[j], c.heading()) / span;
                } else {
                    u = ((c.x() - bx[j]) * segX + (c.y() - by[j]) * segY) / lengthSq;
                }
                u = Math.max(0, Math.min(1, u));
                double px = bx[j] + u * segX;
                double py = by[j] + u * segY;
                double heading = Angles.lerp(bh[j], bh[k], u);
                double distance = Math.hypot(c.x() - px, c.y() - py);
                double cost = distance + options.headingWeight() * Math.abs(Angles.difference(heading, c.heading()));
                double segmentTime = bt[j] + u * (bt[k] - bt[j]);
                if (cost < bestCost - 1e-12 || (Math.abs(cost - bestCost) <= 1e-12 && segmentTime < bestTime)) {
                    bestCost = cost;
                    bestDistance = distance;
                    bestTime = segmentTime;
                    bestPose = Pose.of(px, py, heading);
                    bestSegment = j;
                }
            }
            if (bestTime < lastMatchedTime) {
                bestTime = lastMatchedTime;
            }
            lastMatchedTime = bestTime;

            Pose ghost = baseline.poseAt(t);
            double candidateSpeed = candidate.velocity(i).speed();
            double baselineSpeed = baseline.velocity(bestSegment).speed();
            result.add(new GhostSample(
                    t,
                    c,
                    ghost,
                    c.distanceTo(ghost),
                    ghost.headingDifferenceTo(c),
                    bestPose,
                    bestDistance,
                    bestPose.headingDifferenceTo(c),
                    bestTime,
                    t - bestTime,
                    candidateSpeed - baselineSpeed));
        }
        return new TrajectoryComparison(result);
    }

    public List<GhostSample> samples() {
        return samples;
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    /** Largest time-aligned position error, meters. */
    public double maxPositionError() {
        return Stats.max(column(Column.POSITION_ERROR));
    }

    /** Mean time-aligned position error, meters. */
    public double meanPositionError() {
        return Stats.mean(column(Column.POSITION_ERROR));
    }

    /** Largest distance from the baseline path, meters. */
    public double maxPathDeviation() {
        return Stats.max(column(Column.PATH_DEVIATION));
    }

    /** Mean distance from the baseline path, meters. */
    public double meanPathDeviation() {
        return Stats.mean(column(Column.PATH_DEVIATION));
    }

    /** Largest absolute path-aligned heading error, radians. */
    public double maxHeadingError() {
        return Stats.max(Stats.abs(column(Column.PATH_HEADING_ERROR)));
    }

    /** Mean absolute path-aligned heading error, radians. */
    public double meanHeadingError() {
        return Stats.mean(Stats.abs(column(Column.PATH_HEADING_ERROR)));
    }

    /** Mean absolute speed difference at matched points, m/s. */
    public double meanSpeedDifference() {
        return Stats.mean(Stats.abs(column(Column.SPEED_DIFFERENCE)));
    }

    /** Timing offset at the last sample: how far behind (positive) or ahead the candidate ended. */
    public double finalTimingOffset() {
        return samples.isEmpty() ? Double.NaN : samples.get(samples.size() - 1).timingOffset();
    }

    private enum Column { POSITION_ERROR, PATH_DEVIATION, PATH_HEADING_ERROR, SPEED_DIFFERENCE }

    private double[] column(Column column) {
        double[] values = new double[samples.size()];
        for (int i = 0; i < values.length; i++) {
            GhostSample s = samples.get(i);
            values[i] = switch (column) {
                case POSITION_ERROR -> s.positionError();
                case PATH_DEVIATION -> s.pathDeviation();
                case PATH_HEADING_ERROR -> s.pathHeadingError();
                case SPEED_DIFFERENCE -> s.speedDifference();
            };
        }
        return values;
    }
}
