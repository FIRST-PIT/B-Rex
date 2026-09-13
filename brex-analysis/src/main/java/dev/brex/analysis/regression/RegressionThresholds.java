package dev.brex.analysis.regression;

/**
 * Limits that decide when a difference from the baseline counts as a regression. All values are
 * SI units (seconds, meters, radians). Set a limit to {@code Double.POSITIVE_INFINITY} to disable
 * that check.
 *
 * @param maxDurationIncrease allowed slowdown of the whole run, seconds
 * @param maxMeanPathDeviation allowed mean distance from the baseline path, meters
 * @param maxPathDeviation allowed worst-case distance from the baseline path, meters
 * @param maxFinalPositionError allowed distance between the two end positions, meters
 * @param maxFinalHeadingError allowed difference between the two end headings, radians
 * @param maxEventDelay allowed delay of any baseline event occurrence, seconds
 * @param maxMechanismSlowdown allowed increase in time spent in any mechanism state, seconds
 * @param maxLocalizationErrorIncrease allowed increase of mean {@code localization.error}, meters
 * @param requireBaselineEvents fail when an event occurrence from the baseline is missing
 */
public record RegressionThresholds(
        double maxDurationIncrease,
        double maxMeanPathDeviation,
        double maxPathDeviation,
        double maxFinalPositionError,
        double maxFinalHeadingError,
        double maxEventDelay,
        double maxMechanismSlowdown,
        double maxLocalizationErrorIncrease,
        boolean requireBaselineEvents) {

    public static final RegressionThresholds DEFAULTS = new RegressionThresholds(
            0.5,
            0.03,
            0.10,
            0.025,
            Math.toRadians(2.0),
            0.5,
            0.25,
            0.01,
            true);

    public RegressionThresholds {
        requireNonNegative("maxDurationIncrease", maxDurationIncrease);
        requireNonNegative("maxMeanPathDeviation", maxMeanPathDeviation);
        requireNonNegative("maxPathDeviation", maxPathDeviation);
        requireNonNegative("maxFinalPositionError", maxFinalPositionError);
        requireNonNegative("maxFinalHeadingError", maxFinalHeadingError);
        requireNonNegative("maxEventDelay", maxEventDelay);
        requireNonNegative("maxMechanismSlowdown", maxMechanismSlowdown);
        requireNonNegative("maxLocalizationErrorIncrease", maxLocalizationErrorIncrease);
    }

    private static void requireNonNegative(String name, double value) {
        if (Double.isNaN(value) || value < 0) {
            throw new IllegalArgumentException("Regression threshold " + name + " must be >= 0 (was " + value + ")");
        }
    }
}
