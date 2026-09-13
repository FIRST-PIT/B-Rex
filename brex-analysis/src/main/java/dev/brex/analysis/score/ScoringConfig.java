package dev.brex.analysis.score;

/**
 * Weights and curves for the autonomous score. Every formula is documented in
 * {@code docs/scoring.md}; defaults are chosen for a 30-second FTC autonomous period and a
 * typical odometry-localized robot.
 *
 * @param speedWeight weight of the speed metric
 * @param accuracyWeight weight of the accuracy metric
 * @param consistencyWeight weight of the consistency metric
 * @param mechanismsWeight weight of the mechanisms metric
 * @param reliabilityWeight weight of the reliability metric
 * @param targetSeconds duration that earns a full speed score; NaN uses the baseline duration
 * @param timeLimitSeconds duration that earns a zero speed score (the autonomous period)
 * @param historyWindow number of most recent runs (including the scored one) used for
 *     consistency and reliability
 * @param minConsistencyRuns fewest runs needed before consistency is scored
 * @param position curve for position errors and spreads, meters
 * @param heading curve for heading errors and spreads, radians
 * @param durationSpread curve for the standard deviation of durations, seconds
 * @param timing curve for mechanism state duration differences, seconds
 */
public record ScoringConfig(
        double speedWeight,
        double accuracyWeight,
        double consistencyWeight,
        double mechanismsWeight,
        double reliabilityWeight,
        double targetSeconds,
        double timeLimitSeconds,
        int historyWindow,
        int minConsistencyRuns,
        Falloff position,
        Falloff heading,
        Falloff durationSpread,
        Falloff timing) {

    public static final ScoringConfig DEFAULTS = new ScoringConfig(
            1, 1, 1, 1, 1,
            Double.NaN,
            30.0,
            10,
            3,
            new Falloff(0.01, 0.10),
            new Falloff(Math.toRadians(1), Math.toRadians(10)),
            new Falloff(0.1, 1.0),
            new Falloff(0.1, 1.0));

    public ScoringConfig {
        for (double weight : new double[] {speedWeight, accuracyWeight, consistencyWeight, mechanismsWeight,
                reliabilityWeight}) {
            if (Double.isNaN(weight) || weight < 0) {
                throw new IllegalArgumentException("Scoring weights must be >= 0 (was " + weight + ")");
            }
        }
        if (!(timeLimitSeconds > 0)) {
            throw new IllegalArgumentException("timeLimitSeconds must be > 0 (was " + timeLimitSeconds + ")");
        }
        if (historyWindow < 1) {
            throw new IllegalArgumentException("historyWindow must be >= 1 (was " + historyWindow + ")");
        }
        if (minConsistencyRuns < 2) {
            throw new IllegalArgumentException("minConsistencyRuns must be >= 2 (was " + minConsistencyRuns + ")");
        }
    }

    public ScoringConfig withWeights(double speed, double accuracy, double consistency, double mechanisms,
            double reliability) {
        return new ScoringConfig(speed, accuracy, consistency, mechanisms, reliability, targetSeconds,
                timeLimitSeconds, historyWindow, minConsistencyRuns, position, heading, durationSpread, timing);
    }

    public ScoringConfig withTargetSeconds(double target) {
        return new ScoringConfig(speedWeight, accuracyWeight, consistencyWeight, mechanismsWeight, reliabilityWeight,
                target, timeLimitSeconds, historyWindow, minConsistencyRuns, position, heading, durationSpread,
                timing);
    }
}
