package dev.brex.core.config;

import dev.brex.core.geometry.DistanceUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Project configuration read from {@code brex.properties}.
 *
 * <p>Physical values carry their unit, which avoids the most common tuning mistake:
 *
 * <pre>
 * units.distance = in
 * regression.maxDurationIncrease = 0.5s
 * regression.maxFinalPositionError = 1in
 * regression.maxFinalHeadingError = 2deg
 * # stricter limit for one routine
 * routine.blue-left.regression.maxDurationIncrease = 0.3s
 * </pre>
 *
 * <p>Every lookup made through {@link #forRoutine(String)} checks
 * {@code routine.<name>.<key>} before {@code <key>}. See {@code docs/configuration.md} for all
 * keys.
 */
public final class BrexConfig {

    public static final String FILE_NAME = "brex.properties";
    public static final String ROUTINE_PREFIX = "routine.";

    /** Keys B-rex understands; anything else is reported as a probable typo. */
    public static final Set<String> KNOWN_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "project.runsDir",
            "project.baselinesDir",
            "units.distance",
            "regression.maxDurationIncrease",
            "regression.maxMeanPathDeviation",
            "regression.maxPathDeviation",
            "regression.maxFinalPositionError",
            "regression.maxFinalHeadingError",
            "regression.maxEventDelay",
            "regression.maxMechanismSlowdown",
            "regression.maxLocalizationErrorIncrease",
            "regression.requireBaselineEvents",
            "scoring.weight.speed",
            "scoring.weight.accuracy",
            "scoring.weight.consistency",
            "scoring.weight.mechanisms",
            "scoring.weight.reliability",
            "scoring.targetDuration",
            "scoring.timeLimit",
            "scoring.historyWindow",
            "scoring.minConsistencyRuns",
            "mechanisms.idleStates",
            "timing.idleSpeed",
            "timing.idleTurnRate",
            "timing.minIdleDuration",
            "montecarlo.startX",
            "montecarlo.startY",
            "montecarlo.startHeading",
            "montecarlo.sensorNoise",
            "montecarlo.localizationDrift",
            "montecarlo.mechanismTiming",
            "montecarlo.maxFinalPositionError",
            "montecarlo.maxFinalHeadingError",
            "montecarlo.maxDuration")));

    private final Properties properties;
    private final String source;
    private final String routine;

    private BrexConfig(Properties properties, String source, String routine) {
        this.properties = properties;
        this.source = source;
        this.routine = routine;
    }

    /** A configuration with no keys set: every lookup returns its default. */
    public static BrexConfig empty() {
        return new BrexConfig(new Properties(), "defaults", null);
    }

    /** Loads a properties file. A missing file yields {@link #empty()}. */
    public static BrexConfig load(File file) throws IOException {
        if (!file.exists()) {
            return empty();
        }
        InputStream in = new FileInputStream(file);
        try {
            return parse(new InputStreamReader(in, "UTF-8"), file.getPath());
        } finally {
            in.close();
        }
    }

    public static BrexConfig parse(String text, String source) {
        try {
            return parse(new StringReader(text), source);
        } catch (IOException e) {
            throw new ConfigException("Could not read " + source, e);
        }
    }

    private static BrexConfig parse(Reader reader, String source) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(source + ": " + e.getMessage(), e);
        }
        return new BrexConfig(properties, source, null);
    }

    /** A view that prefers {@code routine.<name>.*} overrides. */
    public BrexConfig forRoutine(String name) {
        return new BrexConfig(properties, source, name);
    }

    /** Where this configuration came from, used in error messages. */
    public String source() {
        return source;
    }

    public boolean has(String key) {
        return raw(key) != null;
    }

    public String string(String key, String fallback) {
        String value = raw(key);
        return value == null ? fallback : value;
    }

    public double number(String key, double fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw invalid(key, value, "a number");
        }
    }

    public int integer(String key, int fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalid(key, value, "a whole number");
        }
    }

    public boolean bool(String key, boolean fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
            return false;
        }
        throw invalid(key, value, "true or false");
    }

    /** A comma-separated list with surrounding whitespace removed. */
    public List<String> list(String key, List<String> fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /** A duration such as {@code 0.5s}, {@code 250ms} or a bare number of seconds. */
    public double seconds(String key, double fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        Quantity q = Quantity.parse(value, key, source);
        if (q.unit.isEmpty() || q.unit.equals("s")) {
            return q.value;
        }
        if (q.unit.equals("ms")) {
            return q.value / 1000.0;
        }
        throw invalid(key, value, "a duration such as 0.5s or 250ms");
    }

    /** A distance such as {@code 3cm}, {@code 1.5in} or {@code 0.02m}. A unit is required. */
    public double meters(String key, double fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        Quantity q = Quantity.parse(value, key, source);
        if (q.unit.isEmpty()) {
            throw new ConfigException(source + ": " + key + " = " + value
                    + " is ambiguous; add a unit, for example " + value + "cm or " + value + "in");
        }
        try {
            return DistanceUnit.parse(q.unit).toMeters(q.value);
        } catch (IllegalArgumentException e) {
            throw invalid(key, value, "a distance such as 3cm, 1in or 0.02m");
        }
    }

    /** An angle such as {@code 2deg} or {@code 0.03rad}. A unit is required. */
    public double radians(String key, double fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        Quantity q = Quantity.parse(value, key, source);
        if (q.unit.equals("deg") || q.unit.equals("°")) {
            return Math.toRadians(q.value);
        }
        if (q.unit.equals("rad")) {
            return q.value;
        }
        if (q.unit.isEmpty()) {
            throw new ConfigException(source + ": " + key + " = " + value
                    + " is ambiguous; add a unit, for example " + value + "deg");
        }
        throw invalid(key, value, "an angle such as 2deg or 0.03rad");
    }

    /** A speed such as {@code 2cm/s} or {@code 1in/s}. */
    public double metersPerSecond(String key, double fallback) {
        String value = raw(key);
        if (value == null) {
            return fallback;
        }
        Quantity q = Quantity.parse(value, key, source);
        if (!q.unit.endsWith("/s")) {
            throw invalid(key, value, "a speed such as 2cm/s");
        }
        try {
            return DistanceUnit.parse(q.unit.substring(0, q.unit.length() - 2)).toMeters(q.value);
        } catch (IllegalArgumentException e) {
            throw invalid(key, value, "a speed such as 2cm/s");
        }
    }

    /** The unit for displaying distances ({@code units.distance}); defaults to centimeters. */
    public DistanceUnit distanceUnit() {
        String value = raw("units.distance");
        if (value == null) {
            return DistanceUnit.CENTIMETERS;
        }
        try {
            return DistanceUnit.parse(value);
        } catch (IllegalArgumentException e) {
            throw invalid("units.distance", value, "m, cm, mm or in");
        }
    }

    /** Keys present in the file that B-rex does not recognize, sorted. */
    public List<String> unknownKeys() {
        Set<String> unknown = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            String base = key;
            if (key.startsWith(ROUTINE_PREFIX)) {
                int dot = key.indexOf('.', ROUTINE_PREFIX.length());
                base = dot < 0 ? "" : key.substring(dot + 1);
            }
            if (!KNOWN_KEYS.contains(base)) {
                unknown.add(key);
            }
        }
        return new ArrayList<>(unknown);
    }

    private String raw(String key) {
        if (routine != null) {
            String override = properties.getProperty(ROUTINE_PREFIX + routine + "." + key);
            if (override != null && !override.trim().isEmpty()) {
                return override.trim();
            }
        }
        String value = properties.getProperty(key);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private ConfigException invalid(String key, String value, String expected) {
        String effectiveKey = routine != null && properties.getProperty(ROUTINE_PREFIX + routine + "." + key) != null
                ? ROUTINE_PREFIX + routine + "." + key : key;
        return new ConfigException(source + ": " + effectiveKey + " must be " + expected + ", got '" + value + "'");
    }

    /** A number followed by an optional unit suffix. */
    private static final class Quantity {
        final double value;
        final String unit;

        private Quantity(double value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        static Quantity parse(String text, String key, String source) {
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                    // 'e' only counts as part of the number when followed by a digit or sign.
                    if ((c == 'e' || c == 'E') && (i + 1 >= text.length()
                            || !(Character.isDigit(text.charAt(i + 1)) || text.charAt(i + 1) == '-'))) {
                        break;
                    }
                    i++;
                } else {
                    break;
                }
            }
            try {
                double value = Double.parseDouble(text.substring(0, i).trim());
                return new Quantity(value, text.substring(i).trim().toLowerCase(Locale.ROOT));
            } catch (NumberFormatException e) {
                throw new ConfigException(source + ": " + key + " must start with a number, got '" + text + "'");
            }
        }
    }
}
