package dev.brex.cli;

import dev.brex.analysis.Unit;
import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Consistent formatting of values in human-readable CLI output. */
public final class Fmt {

    private Fmt() {
    }

    public static String seconds(double seconds) {
        return Double.isNaN(seconds) ? "n/a" : String.format(Locale.ROOT, "%.2fs", seconds);
    }

    public static String signedSeconds(double seconds) {
        return Double.isNaN(seconds) ? "n/a" : String.format(Locale.ROOT, "%+.2fs", seconds);
    }

    public static String distance(double meters, DistanceUnit unit) {
        if (Double.isNaN(meters)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, decimals(unit), unit.fromMeters(meters)) + unit.symbol();
    }

    public static String signedDistance(double meters, DistanceUnit unit) {
        if (Double.isNaN(meters)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, decimals(unit).replace("%", "%+"), unit.fromMeters(meters)) + unit.symbol();
    }

    public static String degrees(double radians) {
        return Double.isNaN(radians) ? "n/a" : String.format(Locale.ROOT, "%.1f°", Math.toDegrees(radians));
    }

    public static String signedDegrees(double radians) {
        return Double.isNaN(radians) ? "n/a" : String.format(Locale.ROOT, "%+.1f°", Math.toDegrees(radians));
    }

    public static String score(double score) {
        return Double.isNaN(score) ? "n/a" : String.format(Locale.ROOT, "%.1f", score);
    }

    public static String percent(double percent) {
        return Double.isNaN(percent) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", percent);
    }

    public static String speed(double metersPerSecond, DistanceUnit unit) {
        return Double.isNaN(metersPerSecond) ? "n/a" : distance(metersPerSecond, unit) + "/s";
    }

    public static String pose(Pose pose, DistanceUnit unit) {
        if (pose == null) {
            return "n/a";
        }
        return "(" + distance(pose.x(), unit) + ", " + distance(pose.y(), unit) + ", "
                + String.format(Locale.ROOT, "%.1f°", pose.headingDegrees()) + ")";
    }

    /** A value of an analysis unit, e.g. regression thresholds. */
    public static String quantity(double value, Unit unit, DistanceUnit distanceUnit) {
        return switch (unit) {
            case SECONDS -> seconds(value);
            case METERS -> distance(value, distanceUnit);
            case RADIANS -> degrees(value);
            case METERS_PER_SECOND -> speed(value, distanceUnit);
            case PERCENT -> percent(value);
            case COUNT, NONE -> Double.isNaN(value) ? "n/a"
                    : value == Math.rint(value) ? Long.toString((long) value)
                    : String.format(Locale.ROOT, "%.2f", value);
        };
    }

    public static String date(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown date";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    public static String shortCommit(String commit) {
        if (commit == null) {
            return "-";
        }
        return commit.length() > 7 ? commit.substring(0, 7) : commit;
    }

    public static String status(Run run) {
        return run.status().name().toLowerCase(Locale.ROOT);
    }

    public static String rule(int width) {
        return "━".repeat(width);
    }

    public static String pad(String text, int width) {
        int length = text.codePointCount(0, text.length());
        return length >= width ? text : text + " ".repeat(width - length);
    }

    public static String padLeft(String text, int width) {
        int length = text.codePointCount(0, text.length());
        return length >= width ? text : " ".repeat(width - length) + text;
    }

    private static String decimals(DistanceUnit unit) {
        return switch (unit) {
            case METERS -> "%.3f";
            case MILLIMETERS -> "%.0f";
            default -> "%.1f";
        };
    }
}
