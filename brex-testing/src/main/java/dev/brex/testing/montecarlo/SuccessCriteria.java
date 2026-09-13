package dev.brex.testing.montecarlo;

/**
 * When a simulated run counts as a success. Errors are measured against the reference run.
 *
 * @param maxFinalPositionError largest allowed distance from the reference end position (m)
 * @param maxFinalHeadingError largest allowed difference from the reference end heading (rad)
 * @param maxDuration longest allowed duration (s)
 * @param requireCompletion the simulated run must complete
 * @param requireEvents every event name of the reference must occur
 */
public record SuccessCriteria(double maxFinalPositionError, double maxFinalHeadingError, double maxDuration,
        boolean requireCompletion, boolean requireEvents) {

    public static final SuccessCriteria DEFAULTS = new SuccessCriteria(0.05, Math.toRadians(5), 30, true, true);
}
