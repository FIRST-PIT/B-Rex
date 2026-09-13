package dev.brex.testing.montecarlo;

import java.util.Map;

/**
 * What to vary and how many times. SI units throughout.
 *
 * @param runs number of simulated runs
 * @param seed master seed; the same seed always produces the same results
 * @param startX distribution of start placement error along x (m)
 * @param startY distribution of start placement error along y (m)
 * @param startHeading distribution of start heading error (rad)
 * @param sensorNoise standard deviation of pose estimate noise in every run (m)
 * @param headingNoise standard deviation of heading estimate noise in every run (rad)
 * @param odometryScaleError distribution of the odometry scale error (fraction)
 * @param mechanismTimingScale distribution of the mechanism duration multiplier
 * @param mechanismTimingJitter standard deviation of extra time per active mechanism state (s)
 * @param environment distributions for custom simulation parameters
 */
public record MonteCarloConfig(
        int runs,
        long seed,
        Distribution startX,
        Distribution startY,
        Distribution startHeading,
        double sensorNoise,
        double headingNoise,
        Distribution odometryScaleError,
        Distribution mechanismTimingScale,
        double mechanismTimingJitter,
        Map<String, Distribution> environment) {

    /** 1000 runs with typical FTC placement and localization uncertainty. */
    public static final MonteCarloConfig DEFAULTS = new MonteCarloConfig(1000, 0,
            Distribution.normal(0.01), Distribution.normal(0.01), Distribution.normal(Math.toRadians(1)),
            0.005, Math.toRadians(0.5), Distribution.normal(0.005), Distribution.constant(1), 0.1, Map.of());

    public MonteCarloConfig {
        if (runs < 1) {
            throw new IllegalArgumentException("Monte Carlo needs at least 1 run (was " + runs + ")");
        }
        if (sensorNoise < 0 || headingNoise < 0 || mechanismTimingJitter < 0) {
            throw new IllegalArgumentException("Noise and jitter must be >= 0");
        }
        environment = Map.copyOf(environment);
    }

    public MonteCarloConfig withRuns(int count) {
        return new MonteCarloConfig(count, seed, startX, startY, startHeading, sensorNoise, headingNoise,
                odometryScaleError, mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public MonteCarloConfig withSeed(long newSeed) {
        return new MonteCarloConfig(runs, newSeed, startX, startY, startHeading, sensorNoise, headingNoise,
                odometryScaleError, mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public MonteCarloConfig withStart(Distribution x, Distribution y, Distribution heading) {
        return new MonteCarloConfig(runs, seed, x, y, heading, sensorNoise, headingNoise, odometryScaleError,
                mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public MonteCarloConfig withLocalization(double noise, double headingNoiseRad, Distribution scaleError) {
        return new MonteCarloConfig(runs, seed, startX, startY, startHeading, noise, headingNoiseRad, scaleError,
                mechanismTimingScale, mechanismTimingJitter, environment);
    }

    public MonteCarloConfig withMechanismTiming(Distribution scale, double jitter) {
        return new MonteCarloConfig(runs, seed, startX, startY, startHeading, sensorNoise, headingNoise,
                odometryScaleError, scale, jitter, environment);
    }

    /** No variation at all: every run is the nominal simulation. */
    public MonteCarloConfig withoutVariation() {
        return new MonteCarloConfig(runs, seed, Distribution.ZERO, Distribution.ZERO, Distribution.ZERO, 0, 0,
                Distribution.ZERO, Distribution.constant(1), 0, Map.of());
    }
}
