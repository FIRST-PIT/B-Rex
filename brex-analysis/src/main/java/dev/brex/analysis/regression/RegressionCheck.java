package dev.brex.analysis.regression;

import dev.brex.analysis.Unit;

/**
 * The result of comparing one metric against the baseline.
 *
 * @param id stable machine-readable identifier, e.g. {@code duration}
 * @param title human-readable name, e.g. {@code Duration}
 * @param status outcome
 * @param baseline baseline value, or NaN when not applicable
 * @param current candidate value, or NaN when not applicable
 * @param threshold the limit applied, or NaN when not applicable
 * @param unit unit of {@code baseline}, {@code current} and {@code threshold}
 * @param message one-line explanation, always present for failures and skips
 */
public record RegressionCheck(
        String id,
        String title,
        Status status,
        double baseline,
        double current,
        double threshold,
        Unit unit,
        String message) {

    /** Outcome of a check. */
    public enum Status { PASS, FAIL, SKIP }

    /** {@code current - baseline}. */
    public double delta() {
        return current - baseline;
    }

    public boolean failed() {
        return status == Status.FAIL;
    }

    static RegressionCheck skip(String id, String title, String reason) {
        return new RegressionCheck(id, title, Status.SKIP, Double.NaN, Double.NaN, Double.NaN, Unit.NONE, reason);
    }
}
