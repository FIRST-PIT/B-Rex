package dev.brex.core.telemetry;

import dev.brex.core.util.DoubleArrayBuilder;
import dev.brex.core.util.TimeSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable, time-stamped series of values for one telemetry key such as
 * {@code lift.position}.
 *
 * <p>Numeric and boolean channels store primitive doubles (booleans as 0/1); text channels store
 * strings. Times are seconds since the start of the run and never decrease.
 */
public final class TelemetryChannel {

    private final String key;
    private final ChannelType type;
    private final double[] times;
    private final double[] numbers;
    private final String[] texts;

    private TelemetryChannel(String key, ChannelType type, double[] times, double[] numbers, String[] texts) {
        this.key = requireValidKey(key);
        this.type = type;
        this.times = times;
        this.numbers = numbers;
        this.texts = texts;
        TimeSeries.requireSorted(times, "Telemetry channel '" + key + "'");
    }

    public static TelemetryChannel numeric(String key, double[] times, double[] values) {
        requireSameLength(key, times.length, values.length);
        return new TelemetryChannel(key, ChannelType.NUMBER, times.clone(), values.clone(), null);
    }

    public static TelemetryChannel booleans(String key, double[] times, boolean[] values) {
        requireSameLength(key, times.length, values.length);
        double[] numbers = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = values[i] ? 1 : 0;
        }
        return new TelemetryChannel(key, ChannelType.BOOLEAN, times.clone(), numbers, null);
    }

    public static TelemetryChannel text(String key, double[] times, String[] values) {
        requireSameLength(key, times.length, values.length);
        return new TelemetryChannel(key, ChannelType.TEXT, times.clone(), null, values.clone());
    }

    /** Validates a telemetry key: non-empty, no whitespace. Dots separate namespaces. */
    public static String requireValidKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Telemetry key must not be empty");
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isWhitespace(key.charAt(i))) {
                throw new IllegalArgumentException("Telemetry key '" + key + "' must not contain whitespace");
            }
        }
        return key;
    }

    public String key() {
        return key;
    }

    public ChannelType type() {
        return type;
    }

    public int size() {
        return times.length;
    }

    public boolean isEmpty() {
        return times.length == 0;
    }

    public double time(int index) {
        return times[index];
    }

    /** A copy of the sample times. */
    public double[] times() {
        return times.clone();
    }

    /** The numeric value of a sample. Booleans read as 0 or 1. Not valid for text channels. */
    public double number(int index) {
        requireNumeric();
        return numbers[index];
    }

    /** A copy of the numeric values. Not valid for text channels. */
    public double[] numbers() {
        requireNumeric();
        return numbers.clone();
    }

    public boolean bool(int index) {
        requireNumeric();
        return numbers[index] != 0;
    }

    /** A sample rendered as text, valid for every channel type. */
    public String text(int index) {
        switch (type) {
            case TEXT:
                return texts[index];
            case BOOLEAN:
                return numbers[index] != 0 ? "true" : "false";
            default:
                return formatNumber(numbers[index]);
        }
    }

    /**
     * Index of the sample in effect at time {@code t} (the last sample at or before {@code t}), or
     * -1 if the channel has no sample yet at that time. Telemetry is sample-and-hold.
     */
    public int indexAt(double t) {
        return TimeSeries.indexAtOrBefore(times, t);
    }

    /** The numeric value in effect at {@code t}, or NaN before the first sample. */
    public double numberAt(double t) {
        int index = indexAt(t);
        return index < 0 ? Double.NaN : number(index);
    }

    /** The textual value in effect at {@code t}, or null before the first sample. */
    public String textAt(double t) {
        int index = indexAt(t);
        return index < 0 ? null : text(index);
    }

    /** Returns the samples with {@code from <= time <= to}. */
    public TelemetryChannel slice(double from, double to) {
        int start = 0;
        while (start < times.length && times[start] < from) {
            start++;
        }
        int end = start;
        while (end < times.length && times[end] <= to) {
            end++;
        }
        double[] t = Arrays.copyOfRange(times, start, end);
        return new TelemetryChannel(key, type, t, numbers == null ? null : Arrays.copyOfRange(numbers, start, end),
                texts == null ? null : Arrays.copyOfRange(texts, start, end));
    }

    private void requireNumeric() {
        if (type == ChannelType.TEXT) {
            throw new IllegalStateException("Telemetry channel '" + key + "' holds text, not numbers");
        }
    }

    private static void requireSameLength(String key, int times, int values) {
        if (times != values) {
            throw new IllegalArgumentException("Telemetry channel '" + key + "' has " + times + " timestamps but "
                    + values + " values");
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    @Override
    public String toString() {
        return "TelemetryChannel(" + key + ", " + type.id() + ", " + times.length + " samples)";
    }

    /** Accumulates samples for one channel during a recording. Not thread-safe. */
    public static final class Builder {

        private final String key;
        private final ChannelType type;
        private final DoubleArrayBuilder times = new DoubleArrayBuilder();
        private final DoubleArrayBuilder numbers;
        private final List<String> texts;

        public Builder(String key, ChannelType type) {
            this.key = requireValidKey(key);
            this.type = type;
            this.numbers = type == ChannelType.TEXT ? null : new DoubleArrayBuilder();
            this.texts = type == ChannelType.TEXT ? new ArrayList<String>() : null;
        }

        public String key() {
            return key;
        }

        public ChannelType type() {
            return type;
        }

        public int size() {
            return times.size();
        }

        /**
         * Adds a numeric (or boolean 0/1) sample. Timestamps earlier than the previous sample are
         * clamped so a misbehaving clock can never corrupt the series.
         */
        public void addNumber(double t, double value) {
            if (numbers == null) {
                throw new IllegalStateException("Telemetry channel '" + key + "' holds text, not numbers");
            }
            times.add(clamp(t));
            numbers.add(value);
        }

        public void addText(double t, String value) {
            if (texts == null) {
                throw new IllegalStateException("Telemetry channel '" + key + "' holds " + type.id() + " values");
            }
            times.add(clamp(t));
            texts.add(value);
        }

        private double clamp(double t) {
            return times.isEmpty() ? t : Math.max(t, times.last());
        }

        public TelemetryChannel build() {
            double[] t = times.toArray();
            if (type == ChannelType.TEXT) {
                return new TelemetryChannel(key, type, t, null, texts.toArray(new String[0]));
            }
            return new TelemetryChannel(key, type, t, numbers.toArray(), null);
        }
    }
}
