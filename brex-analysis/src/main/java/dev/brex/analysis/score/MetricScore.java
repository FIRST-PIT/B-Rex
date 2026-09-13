package dev.brex.analysis.score;

/**
 * One component of the autonomous score.
 *
 * @param name metric name, e.g. {@code Speed}
 * @param score 0-100, or NaN when the data needed to compute it is missing
 * @param weight configured weight
 * @param detail the inputs behind the number, or why it could not be computed
 */
public record MetricScore(String name, double score, double weight, String detail) {

    public boolean available() {
        return !Double.isNaN(score);
    }

    static MetricScore unavailable(String name, double weight, String reason) {
        return new MetricScore(name, Double.NaN, weight, reason);
    }
}
