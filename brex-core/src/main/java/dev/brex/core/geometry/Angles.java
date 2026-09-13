package dev.brex.core.geometry;

/** Angle helpers. B-rex stores all angles in radians. */
public final class Angles {

    private static final double TWO_PI = 2 * Math.PI;

    private Angles() {
    }

    /** Wraps an angle into the half-open interval (-π, π]. */
    public static double normalize(double radians) {
        double wrapped = radians % TWO_PI;
        if (wrapped > Math.PI) {
            wrapped -= TWO_PI;
        } else if (wrapped <= -Math.PI) {
            wrapped += TWO_PI;
        }
        return wrapped;
    }

    /** Returns the signed shortest rotation from {@code from} to {@code to}, in (-π, π]. */
    public static double difference(double from, double to) {
        return normalize(to - from);
    }

    /** Interpolates along the shortest arc between two headings. */
    public static double lerp(double from, double to, double fraction) {
        return normalize(from + difference(from, to) * fraction);
    }
}
