package dev.brex.analysis.score;

/**
 * A linear scoring curve: values at or below {@code full} score 1, values at or above
 * {@code zero} score 0, and values in between fall off linearly.
 *
 * @param full largest value that still earns a full score
 * @param zero smallest value that earns nothing
 */
public record Falloff(double full, double zero) {

    public Falloff {
        if (Double.isNaN(full) || Double.isNaN(zero) || zero < full) {
            throw new IllegalArgumentException("Falloff requires zero >= full (full=" + full + ", zero=" + zero + ")");
        }
    }

    public double apply(double value) {
        if (value <= full) {
            return 1;
        }
        if (value >= zero) {
            return 0;
        }
        return 1 - (value - full) / (zero - full);
    }
}
