package dev.brex.sim;

import java.util.Map;

/**
 * The conditions a simulated run is executed under. All values are SI units.
 *
 * @param startOffsetX how far the robot is actually placed from the intended start, along field x (m)
 * @param startOffsetY placement error along field y (m)
 * @param startOffsetHeading placement heading error (rad)
 * @param sensorNoise standard deviation of position noise on the pose estimate (m)
 * @param headingNoise standard deviation of heading noise on the pose estimate (rad)
 * @param odometryScaleError fractional under-measurement of distance travelled; 0.01 means the
 *     localizer measures 1% less than the robot really moves
 * @param mechanismTimingScale multiplier on the duration of active mechanism states (1 = as
 *     recorded, 1.1 = 10% slower)
 * @param mechanismTimingJitter standard deviation of extra time added to each active mechanism
 *     state (s)
 * @param environment free-form named parameters for custom simulations
 */
public record SimulationParameters(
        double startOffsetX,
        double startOffsetY,
        double startOffsetHeading,
        double sensorNoise,
        double headingNoise,
        double odometryScaleError,
        double mechanismTimingScale,
        double mechanismTimingJitter,
        Map<String, Double> environment) {

    /** Everything exactly as recorded. */
    public static final SimulationParameters NOMINAL = new SimulationParameters(0, 0, 0, 0, 0, 0, 1, 0, Map.of());

    public SimulationParameters {
        requireFinite("startOffsetX", startOffsetX);
        requireFinite("startOffsetY", startOffsetY);
        requireFinite("startOffsetHeading", startOffsetHeading);
        requireNonNegative("sensorNoise", sensorNoise);
        requireNonNegative("headingNoise", headingNoise);
        requireFinite("odometryScaleError", odometryScaleError);
        if (!(mechanismTimingScale > 0)) {
            throw new IllegalArgumentException("mechanismTimingScale must be > 0 (was " + mechanismTimingScale + ")");
        }
        requireNonNegative("mechanismTimingJitter", mechanismTimingJitter);
        environment = Map.copyOf(environment);
    }

    public SimulationParameters withStartOffset(double x, double y, double heading) {
        return new SimulationParameters(x, y, heading, sensorNoise, headingNoise, odometryScaleError,
                mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public SimulationParameters withNoise(double position, double heading) {
        return new SimulationParameters(startOffsetX, startOffsetY, startOffsetHeading, position, heading,
                odometryScaleError, mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public SimulationParameters withOdometryScaleError(double error) {
        return new SimulationParameters(startOffsetX, startOffsetY, startOffsetHeading, sensorNoise, headingNoise,
                error, mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public SimulationParameters withMechanismTiming(double scale, double jitter) {
        return new SimulationParameters(startOffsetX, startOffsetY, startOffsetHeading, sensorNoise, headingNoise,
                odometryScaleError, scale, jitter, environment);
    }

    private static void requireFinite(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number (was " + value + ")");
        }
    }

    private static void requireNonNegative(String name, double value) {
        requireFinite(name, value);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0 (was " + value + ")");
        }
    }
}
