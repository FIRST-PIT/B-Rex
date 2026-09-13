package dev.brex.analysis.score;

import java.util.List;

/**
 * The autonomous score: five metrics and their weighted total.
 *
 * <p>The total is the weighted mean of the <em>available</em> metrics. A metric that cannot be
 * computed (for example consistency with a single run) is excluded rather than guessed, and
 * {@link #unavailable()} says which ones were excluded.
 */
public record AutonScore(String runId, List<MetricScore> metrics) {

    public AutonScore {
        metrics = List.copyOf(metrics);
    }

    /** Weighted mean of available metrics, or NaN when none are available or all weights are zero. */
    public double total() {
        double weighted = 0;
        double weights = 0;
        for (MetricScore metric : metrics) {
            if (metric.available() && metric.weight() > 0) {
                weighted += metric.score() * metric.weight();
                weights += metric.weight();
            }
        }
        return weights == 0 ? Double.NaN : weighted / weights;
    }

    public MetricScore metric(String name) {
        return metrics.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<MetricScore> unavailable() {
        return metrics.stream().filter(m -> !m.available()).toList();
    }
}
