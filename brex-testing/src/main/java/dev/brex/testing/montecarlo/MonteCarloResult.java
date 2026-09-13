package dev.brex.testing.montecarlo;

import dev.brex.analysis.Stats;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregated Monte Carlo results. */
public record MonteCarloResult(String routine, String referenceRunId, MonteCarloConfig config,
        SuccessCriteria criteria, List<MonteCarloOutcome> outcomes) {

    public MonteCarloResult {
        outcomes = List.copyOf(outcomes);
    }

    public int runs() {
        return outcomes.size();
    }

    public long successes() {
        return outcomes.stream().filter(MonteCarloOutcome::success).count();
    }

    public long failures() {
        return runs() - successes();
    }

    /** Percentage of successful runs. */
    public double successRate() {
        return 100.0 * successes() / runs();
    }

    /** The run that ended farthest from the reference end position. */
    public MonteCarloOutcome worstCase() {
        return outcomes.stream().max(Comparator.comparingDouble(MonteCarloOutcome::positionError)
                .thenComparingDouble(o -> Math.abs(o.headingError()))).orElseThrow();
    }

    public double positionErrorPercentile(double percentile) {
        return Stats.percentile(outcomes.stream().mapToDouble(MonteCarloOutcome::positionError).toArray(), percentile);
    }

    public double headingErrorPercentile(double percentile) {
        return Stats.percentile(outcomes.stream().mapToDouble(o -> Math.abs(o.headingError())).toArray(), percentile);
    }

    public double durationPercentile(double percentile) {
        return Stats.percentile(outcomes.stream().mapToDouble(MonteCarloOutcome::duration).toArray(), percentile);
    }

    /** How many runs failed for each kind of reason, most common first. */
    public Map<String, Long> failureReasons() {
        Map<String, Long> counts = new TreeMap<>();
        for (MonteCarloOutcome outcome : outcomes) {
            for (String failure : outcome.failures()) {
                String kind = failure.contains(":") ? failure.substring(0, failure.indexOf(':')) : failure;
                counts.merge(kind, 1L, Long::sum);
            }
        }
        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
