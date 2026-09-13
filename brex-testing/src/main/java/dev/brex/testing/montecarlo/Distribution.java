package dev.brex.testing.montecarlo;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * A random distribution for one varied parameter.
 *
 * @param kind the distribution family
 * @param a mean (normal), minimum (uniform) or value (constant)
 * @param b standard deviation (normal) or maximum (uniform); unused for constant
 */
public record Distribution(Kind kind, double a, double b) {

    /** Distribution families. */
    public enum Kind { CONSTANT, NORMAL, UNIFORM }

    public static final Distribution ZERO = constant(0);

    public Distribution {
        if (kind == Kind.NORMAL && b < 0) {
            throw new IllegalArgumentException("Standard deviation must be >= 0 (was " + b + ")");
        }
        if (kind == Kind.UNIFORM && b < a) {
            throw new IllegalArgumentException("Uniform maximum must be >= minimum (" + a + " > " + b + ")");
        }
    }

    public static Distribution constant(double value) {
        return new Distribution(Kind.CONSTANT, value, 0);
    }

    /** Normal distribution centered on 0. */
    public static Distribution normal(double standardDeviation) {
        return new Distribution(Kind.NORMAL, 0, standardDeviation);
    }

    public static Distribution normal(double mean, double standardDeviation) {
        return new Distribution(Kind.NORMAL, mean, standardDeviation);
    }

    public static Distribution uniform(double min, double max) {
        return new Distribution(Kind.UNIFORM, min, max);
    }

    public double sample(RandomGenerator random) {
        return switch (kind) {
            case CONSTANT -> a;
            case NORMAL -> b == 0 ? a : a + random.nextGaussian() * b;
            case UNIFORM -> a == b ? a : a + random.nextDouble() * (b - a);
        };
    }

    @Override
    public String toString() {
        return switch (kind) {
            case CONSTANT -> String.format(Locale.ROOT, "%g", a);
            case NORMAL -> String.format(Locale.ROOT, "normal(mean %g, sd %g)", a, b);
            case UNIFORM -> String.format(Locale.ROOT, "uniform(%g..%g)", a, b);
        };
    }
}
