package dev.brex.core.trajectory;

import dev.brex.core.geometry.Angles;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.util.DoubleArrayBuilder;
import dev.brex.core.util.TimeSeries;
import java.util.Arrays;

/**
 * The robot's recorded path: a time-ordered series of poses with optional velocities.
 *
 * <p>Stored column-wise in primitive arrays. When a velocity was not recorded it is estimated from
 * neighboring poses.
 */
public final class Trajectory {

    private static final Trajectory EMPTY = new Trajectory(new double[0], new double[0], new double[0],
            new double[0], new double[0], new double[0], new double[0]);

    private final double[] t;
    private final double[] x;
    private final double[] y;
    private final double[] heading;
    private final double[] vx;
    private final double[] vy;
    private final double[] omega;

    private Trajectory(double[] t, double[] x, double[] y, double[] heading, double[] vx, double[] vy,
            double[] omega) {
        int n = t.length;
        if (x.length != n || y.length != n || heading.length != n || vx.length != n || vy.length != n
                || omega.length != n) {
            throw new IllegalArgumentException("Trajectory columns must all have " + n + " samples");
        }
        TimeSeries.requireSorted(t, "Trajectory");
        this.t = t;
        this.x = x;
        this.y = y;
        this.heading = heading;
        this.vx = vx;
        this.vy = vy;
        this.omega = omega;
    }

    public static Trajectory empty() {
        return EMPTY;
    }

    /**
     * Creates a trajectory from columns. Velocity columns may be null when velocity was not
     * recorded. Arrays are copied.
     */
    public static Trajectory fromColumns(double[] t, double[] x, double[] y, double[] heading, double[] vx,
            double[] vy, double[] omega) {
        return new Trajectory(t.clone(), x.clone(), y.clone(), normalized(heading), orNaN(vx, t.length),
                orNaN(vy, t.length), orNaN(omega, t.length));
    }

    public int size() {
        return t.length;
    }

    public boolean isEmpty() {
        return t.length == 0;
    }

    public double time(int index) {
        return t[index];
    }

    public Pose pose(int index) {
        return Pose.of(x[index], y[index], heading[index]);
    }

    /** True when at least one sample carries a recorded (not estimated) velocity. */
    public boolean hasRecordedVelocity() {
        for (double v : vx) {
            if (!Double.isNaN(v)) {
                return true;
            }
        }
        return false;
    }

    /** The recorded velocity at a sample, or an estimate from neighboring samples. */
    public Velocity velocity(int index) {
        if (!Double.isNaN(vx[index]) && !Double.isNaN(vy[index]) && !Double.isNaN(omega[index])) {
            return Velocity.of(vx[index], vy[index], omega[index]);
        }
        if (t.length < 2) {
            return Velocity.ZERO;
        }
        int a = Math.max(0, index - 1);
        int b = Math.min(t.length - 1, index + 1);
        double dt = t[b] - t[a];
        if (dt <= 0) {
            return Velocity.ZERO;
        }
        return Velocity.of((x[b] - x[a]) / dt, (y[b] - y[a]) / dt,
                Angles.difference(heading[a], heading[b]) / dt);
    }

    public double startTime() {
        requireNotEmpty();
        return t[0];
    }

    public double endTime() {
        requireNotEmpty();
        return t[t.length - 1];
    }

    public Pose startPose() {
        requireNotEmpty();
        return pose(0);
    }

    public Pose endPose() {
        requireNotEmpty();
        return pose(t.length - 1);
    }

    /**
     * The pose at time {@code time}, linearly interpolated between samples and clamped to the
     * first and last sample.
     */
    public Pose poseAt(double time) {
        requireNotEmpty();
        int i = TimeSeries.indexAtOrBefore(t, time);
        if (i < 0) {
            return pose(0);
        }
        if (i >= t.length - 1) {
            return pose(t.length - 1);
        }
        double dt = t[i + 1] - t[i];
        if (dt <= 0) {
            return pose(i + 1);
        }
        return pose(i).interpolate(pose(i + 1), (time - t[i]) / dt);
    }

    /** The velocity at {@code time}, from the nearest sample at or before it. */
    public Velocity velocityAt(double time) {
        requireNotEmpty();
        return velocity(Math.max(0, TimeSeries.indexAtOrBefore(t, time)));
    }

    /** Total distance travelled along the path, in meters. */
    public double pathLength() {
        double length = 0;
        for (int i = 1; i < t.length; i++) {
            length += Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
        return length;
    }

    /** Samples with {@code from <= time <= to}. */
    public Trajectory slice(double from, double to) {
        int start = 0;
        while (start < t.length && t[start] < from) {
            start++;
        }
        int end = start;
        while (end < t.length && t[end] <= to) {
            end++;
        }
        return new Trajectory(Arrays.copyOfRange(t, start, end), Arrays.copyOfRange(x, start, end),
                Arrays.copyOfRange(y, start, end), Arrays.copyOfRange(heading, start, end),
                Arrays.copyOfRange(vx, start, end), Arrays.copyOfRange(vy, start, end),
                Arrays.copyOfRange(omega, start, end));
    }

    public double[] times() {
        return t.clone();
    }

    public double[] xs() {
        return x.clone();
    }

    public double[] ys() {
        return y.clone();
    }

    public double[] headings() {
        return heading.clone();
    }

    /** Recorded vx values; NaN where velocity was not recorded. */
    public double[] recordedVx() {
        return vx.clone();
    }

    public double[] recordedVy() {
        return vy.clone();
    }

    public double[] recordedOmega() {
        return omega.clone();
    }

    private void requireNotEmpty() {
        if (t.length == 0) {
            throw new IllegalStateException("Trajectory is empty (no poses were recorded)");
        }
    }

    private static double[] normalized(double[] headings) {
        double[] result = new double[headings.length];
        for (int i = 0; i < headings.length; i++) {
            result[i] = Angles.normalize(headings[i]);
        }
        return result;
    }

    private static double[] orNaN(double[] values, int length) {
        if (values != null) {
            return values.clone();
        }
        double[] result = new double[length];
        Arrays.fill(result, Double.NaN);
        return result;
    }

    @Override
    public String toString() {
        return "Trajectory(" + t.length + " samples)";
    }

    /** Accumulates poses during a recording. Not thread-safe. */
    public static final class Builder {

        private final DoubleArrayBuilder t = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder x = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder y = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder heading = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder vx = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder vy = new DoubleArrayBuilder(512);
        private final DoubleArrayBuilder omega = new DoubleArrayBuilder(512);

        public Builder add(double time, Pose pose) {
            return add(time, pose.x(), pose.y(), pose.heading(), Double.NaN, Double.NaN, Double.NaN);
        }

        public Builder add(double time, Pose pose, Velocity velocity) {
            return add(time, pose.x(), pose.y(), pose.heading(), velocity.vx(), velocity.vy(), velocity.omega());
        }

        /** Adds a sample. Timestamps earlier than the previous sample are clamped. */
        public Builder add(double time, double xMeters, double yMeters, double headingRadians, double vxMps,
                double vyMps, double omegaRps) {
            t.add(t.isEmpty() ? time : Math.max(time, t.last()));
            x.add(xMeters);
            y.add(yMeters);
            heading.add(Angles.normalize(headingRadians));
            vx.add(vxMps);
            vy.add(vyMps);
            omega.add(omegaRps);
            return this;
        }

        public int size() {
            return t.size();
        }

        public Trajectory build() {
            return new Trajectory(t.toArray(), x.toArray(), y.toArray(), heading.toArray(), vx.toArray(),
                    vy.toArray(), omega.toArray());
        }
    }
}
