package dev.brex.sim;

import dev.brex.core.util.TimeSeries;
import java.util.Arrays;

/**
 * A monotone, piecewise-linear mapping from reference time to simulated time. Used to stretch or
 * shrink the parts of a routine where mechanisms are working.
 */
public final class TimeWarp {

    private final double[] reference;
    private final double[] simulated;

    private TimeWarp(double[] reference, double[] simulated) {
        this.reference = reference;
        this.simulated = simulated;
    }

    public static TimeWarp identity() {
        return new TimeWarp(new double[] {0}, new double[] {0});
    }

    /**
     * Builds a warp from non-overlapping reference intervals, each lengthened by
     * {@code extra[i]} seconds (negative shortens it, never below zero length).
     */
    public static TimeWarp stretching(double[] starts, double[] ends, double[] extra) {
        double[] ref = new double[starts.length * 2 + 1];
        double[] sim = new double[starts.length * 2 + 1];
        double shift = 0;
        int k = 1;
        for (int i = 0; i < starts.length; i++) {
            double length = ends[i] - starts[i];
            ref[k] = starts[i];
            sim[k++] = starts[i] + shift;
            shift += Math.max(-length, extra[i]);
            ref[k] = ends[i];
            sim[k++] = ends[i] + shift;
        }
        TimeSeries.requireSorted(ref, "Time warp");
        return new TimeWarp(ref, sim);
    }

    /** Simulated time for a reference time. Beyond the last breakpoint the shift stays constant. */
    public double toSimulated(double referenceTime) {
        return map(reference, simulated, referenceTime);
    }

    /** Reference time for a simulated time (the inverse mapping). */
    public double toReference(double simulatedTime) {
        return map(simulated, reference, simulatedTime);
    }

    private static double map(double[] from, double[] to, double t) {
        int i = TimeSeries.indexAtOrBefore(from, t);
        if (i < 0) {
            return t - from[0] + to[0];
        }
        if (i == from.length - 1) {
            return to[i] + (t - from[i]);
        }
        double span = from[i + 1] - from[i];
        if (span <= 0) {
            return to[i + 1];
        }
        return to[i] + (t - from[i]) * (to[i + 1] - to[i]) / span;
    }

    @Override
    public String toString() {
        return "TimeWarp(" + Arrays.toString(reference) + " -> " + Arrays.toString(simulated) + ")";
    }
}
