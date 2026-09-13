package dev.brex.core.geometry;

import java.util.Locale;

/** Field-relative robot velocity: meters per second and radians per second. */
public final class Velocity {

    public static final Velocity ZERO = new Velocity(0, 0, 0);

    private final double vx;
    private final double vy;
    private final double omega;

    private Velocity(double vx, double vy, double omega) {
        this.vx = vx;
        this.vy = vy;
        this.omega = omega;
    }

    public static Velocity of(double vxMetersPerSecond, double vyMetersPerSecond, double omegaRadiansPerSecond) {
        return new Velocity(vxMetersPerSecond, vyMetersPerSecond, omegaRadiansPerSecond);
    }

    public double vx() {
        return vx;
    }

    public double vy() {
        return vy;
    }

    public double omega() {
        return omega;
    }

    /** Translational speed in meters per second. */
    public double speed() {
        return Math.hypot(vx, vy);
    }

    public boolean isKnown() {
        return !Double.isNaN(vx) && !Double.isNaN(vy) && !Double.isNaN(omega);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Velocity)) {
            return false;
        }
        Velocity other = (Velocity) o;
        return Double.compare(vx, other.vx) == 0 && Double.compare(vy, other.vy) == 0
                && Double.compare(omega, other.omega) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(vx);
        result = 31 * result + Double.hashCode(vy);
        return 31 * result + Double.hashCode(omega);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Velocity(vx=%.3fm/s, vy=%.3fm/s, omega=%.3frad/s)", vx, vy, omega);
    }
}
