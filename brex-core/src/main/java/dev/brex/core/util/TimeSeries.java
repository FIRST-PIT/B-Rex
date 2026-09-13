package dev.brex.core.util;

/** Search helpers for sorted sample-time arrays. */
public final class TimeSeries {

    private TimeSeries() {
    }

    /**
     * Returns the index of the last sample whose time is {@code <= t}, or {@code -1} if every
     * sample is later than {@code t}. {@code times} must be sorted ascending.
     */
    public static int indexAtOrBefore(double[] times, double t) {
        int lo = 0;
        int hi = times.length - 1;
        int result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    /** Throws if {@code times} is not sorted in non-decreasing order. */
    public static void requireSorted(double[] times, String what) {
        for (int i = 1; i < times.length; i++) {
            if (times[i] < times[i - 1]) {
                throw new IllegalArgumentException(what + " timestamps must not decrease (index " + i + ": "
                        + times[i - 1] + "s -> " + times[i] + "s)");
            }
        }
    }
}
