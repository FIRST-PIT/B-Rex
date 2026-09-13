package dev.brex.testing.benchmark;

import dev.brex.analysis.Stats;
import dev.brex.core.run.Run;
import java.util.Comparator;
import java.util.List;

/**
 * Performance statistics over the recorded runs of one routine, e.g. the last 20 practice runs.
 *
 * @param routine routine name
 * @param runs all runs considered, oldest first
 */
public record RunBenchmark(String routine, List<Run> runs) {

    public RunBenchmark {
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("A benchmark needs at least one run of " + routine);
        }
        runs = List.copyOf(runs);
    }

    /** The most recent {@code limit} runs. */
    public static RunBenchmark of(String routine, List<Run> history, int limit) {
        return new RunBenchmark(routine, history.subList(Math.max(0, history.size() - limit), history.size()));
    }

    public int count() {
        return runs.size();
    }

    public List<Run> successfulRuns() {
        return runs.stream().filter(Run::succeeded).toList();
    }

    /** Percentage of runs that completed without errors. */
    public double reliability() {
        return 100.0 * successfulRuns().size() / runs.size();
    }

    /** Durations of successful runs. */
    public double[] durations() {
        return successfulRuns().stream().mapToDouble(Run::duration).toArray();
    }

    public double meanDuration() {
        return Stats.mean(durations());
    }

    public double durationStdDev() {
        return Stats.stdDev(durations());
    }

    public double durationPercentile(double percentile) {
        return Stats.percentile(durations(), percentile);
    }

    /** The fastest successful run, or null. */
    public Run fastest() {
        return successfulRuns().stream().min(Comparator.comparingDouble(Run::duration)).orElse(null);
    }

    /** The slowest successful run, or null. */
    public Run slowest() {
        return successfulRuns().stream().max(Comparator.comparingDouble(Run::duration)).orElse(null);
    }

    /** Mean game points per run. */
    public double meanPoints() {
        return runs.stream().mapToDouble(Run::pointsScored).average().orElse(Double.NaN);
    }
}
