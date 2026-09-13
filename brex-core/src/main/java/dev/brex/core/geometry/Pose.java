package dev.brex.core.geometry;

import java.util.Locale;

/**
 * A field-relative robot pose: position in meters and heading in radians.
 *
 * <p>B-rex does not impose a field coordinate convention; it only requires that every pose in a
 * run uses the same one. Use {@link #ofInches} when your localizer works in inches and degrees.
 */
public final class Pose {

    public static final Pose ORIGIN = new Pose(0, 0, 0);

    private final double x;
    private final double y;
    private final double heading;

    private Pose(double x, double y, double heading) {
        this.x = x;
        this.y = y;
        this.heading = heading;
    }

    /** Creates a pose from meters and radians. The heading is normalized to (-π, π]. */
    public static Pose of(double xMeters, double yMeters, double headingRadians) {
        return new Pose(xMeters, yMeters, Angles.normalize(headingRadians));
    }

    /** Creates a pose from inches and degrees, the units most FTC localizers report. */
    public static Pose ofInches(double xInches, double yInches, double headingDegrees) {
        return of(DistanceUnit.INCHES.toMeters(xInches), DistanceUnit.INCHES.toMeters(yInches),
                Math.toRadians(headingDegrees));
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** Heading in radians, normalized to (-π, π]. */
    public double heading() {
        return heading;
    }

    public double headingDegrees() {
        return Math.toDegrees(heading);
    }

    /** Straight-line distance between the two positions, in meters. */
    public double distanceTo(Pose other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    /** Signed shortest rotation from this heading to the other heading, in radians. */
    public double headingDifferenceTo(Pose other) {
        return Angles.difference(heading, other.heading);
    }

    /** Linear interpolation of position with shortest-arc interpolation of heading. */
    public Pose interpolate(Pose other, double fraction) {
        return new Pose(x + (other.x - x) * fraction, y + (other.y - y) * fraction,
                Angles.lerp(heading, other.heading, fraction));
    }

    public Pose plus(double dx, double dy, double dHeading) {
        return of(x + dx, y + dy, heading + dHeading);
    }

    public boolean isFinite() {
        return !Double.isNaN(x) && !Double.isInfinite(x) && !Double.isNaN(y) && !Double.isInfinite(y)
                && !Double.isNaN(heading) && !Double.isInfinite(heading);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pose)) {
            return false;
        }
        Pose other = (Pose) o;
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0
                && Double.compare(heading, other.heading) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        return 31 * result + Double.hashCode(heading);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Pose(x=%.3fm, y=%.3fm, heading=%.1f°)", x, y, headingDegrees());
    }
}
