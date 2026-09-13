package dev.brex.analysis.compare;

/**
 * The n-th occurrence of an event (or mechanism state entry) in the baseline matched with the
 * n-th occurrence in the candidate.
 *
 * @param key event name, or {@code mechanism:STATE} for mechanism states
 * @param occurrence zero-based occurrence index
 * @param baselineTime seconds, or NaN when it only occurred in the candidate
 * @param candidateTime seconds, or NaN when it only occurred in the baseline
 */
public record OccurrenceMatch(String key, int occurrence, double baselineTime, double candidateTime) {

    /** Whether the occurrence appears in both runs, only the baseline, or only the candidate. */
    public enum Status { MATCHED, MISSING, EXTRA }

    public Status status() {
        if (Double.isNaN(candidateTime)) {
            return Status.MISSING;
        }
        if (Double.isNaN(baselineTime)) {
            return Status.EXTRA;
        }
        return Status.MATCHED;
    }

    /** {@code candidateTime - baselineTime}; positive means later. NaN unless matched. */
    public double delta() {
        return candidateTime - baselineTime;
    }

    /** Human-readable label, e.g. {@code intake.start} or {@code intake.start #2}. */
    public String label() {
        return occurrence == 0 ? key : key + " #" + (occurrence + 1);
    }
}
