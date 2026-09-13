package dev.brex.analysis;

import java.util.Arrays;
import java.util.Collection;

/** Small descriptive-statistics helpers that ignore NaN values. */
public final class Stats {

    private Stats() {
    }

    public static double mean(double[] values) {
        double sum = 0;
        int n = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    public static double mean(Collection<Double> values) {
        return mean(toArray(values));
    }

    public static double max(double[] values) {
        double max = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && (Double.isNaN(max) || v > max)) {
                max = v;
            }
        }
        return max;
    }

    public static double min(double[] values) {
        double min = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && (Double.isNaN(min) || v < min)) {
                min = v;
            }
        }
        return min;
    }

    /** Population standard deviation. NaN for fewer than two values. */
    public static double stdDev(double[] values) {
        double mean = mean(values);
        double sum = 0;
        int n = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += (v - mean) * (v - mean);
                n++;
            }
        }
        return n < 2 ? Double.NaN : Math.sqrt(sum / n);
    }

    public static double stdDev(Collection<Double> values) {
        return stdDev(toArray(values));
    }

    /** Linear-interpolated percentile, {@code p} in [0, 100]. */
    public static double percentile(double[] values, double p) {
        double[] sorted = Arrays.stream(values).filter(v -> !Double.isNaN(v)).sorted().toArray();
        if (sorted.length == 0) {
            return Double.NaN;
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo);
    }

    public static double[] abs(double[] values) {
        return Arrays.stream(values).map(Math::abs).toArray();
    }

    private static double[] toArray(Collection<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
