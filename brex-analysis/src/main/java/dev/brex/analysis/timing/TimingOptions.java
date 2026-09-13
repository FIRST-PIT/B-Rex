package dev.brex.analysis.timing;

import dev.brex.analysis.IdleStates;

/**
 * Thresholds for timing analysis.
 *
 * @param idleSpeed below this translational speed the drivetrain counts as stopped (m/s)
 * @param idleTurnRate below this angular speed the drivetrain counts as stopped (rad/s)
 * @param minIdleDuration shorter gaps are not reported as idle periods (s)
 * @param mergeGap activity separated by less than this is treated as continuous (s), which keeps
 *     noisy velocity estimates from fragmenting a drive
 * @param idleStates mechanism states that count as inactive
 */
public record TimingOptions(double idleSpeed, double idleTurnRate, double minIdleDuration, double mergeGap,
        IdleStates idleStates) {

    public static final TimingOptions DEFAULTS = new TimingOptions(0.02, Math.toRadians(5), 0.25, 0.1,
            IdleStates.DEFAULT);

    public TimingOptions {
        if (idleSpeed < 0 || idleTurnRate < 0 || minIdleDuration < 0 || mergeGap < 0) {
            throw new IllegalArgumentException("Timing thresholds must be >= 0");
        }
    }

    public TimingOptions withIdleStates(IdleStates states) {
        return new TimingOptions(idleSpeed, idleTurnRate, minIdleDuration, mergeGap, states);
    }
}
