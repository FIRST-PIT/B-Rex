package dev.brex.analysis.score;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.MechanismDurations;
import dev.brex.analysis.Stats;
import dev.brex.analysis.compare.OccurrenceMatch;
import dev.brex.analysis.compare.RunComparison;
import dev.brex.core.geometry.Angles;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes the deterministic autonomous score. The same inputs always produce the same score.
 *
 * <p>Formulas (all metrics are 0-100; {@code f(v)} is a {@link Falloff} curve):
 *
 * <ul>
 *   <li><b>Speed</b> = 0 if the run did not complete, else {@code 100 × f(duration)} with full
 *       score at the target duration and zero at the time limit.</li>
 *   <li><b>Accuracy</b> = {@code 100 × mean(f_pos(final position error), f_heading(final heading
 *       error), f_pos(mean path deviation))} against the baseline.</li>
 *   <li><b>Consistency</b> = {@code 100 × mean(f_duration(σ duration), f_pos(end position spread),
 *       f_heading(σ end heading))} over the recent run history.</li>
 *   <li><b>Mechanisms</b> = {@code 100 × mean(presence, order, timing)} against the baseline's
 *       events and mechanism states.</li>
 *   <li><b>Reliability</b> = {@code 100 × successful runs / runs} over the recent history.</li>
 * </ul>
 */
public final class AutonScorer {

    private final ScoringConfig config;
    private final IdleStates idleStates;

    public AutonScorer(ScoringConfig config) {
        this(config, IdleStates.DEFAULT);
    }

    public AutonScorer(ScoringConfig config, IdleStates idleStates) {
        this.config = config;
        this.idleStates = idleStates;
    }

    /**
     * Scores {@code run}.
     *
     * @param baseline known-good run of the same routine, or null
     * @param history earlier runs of the same routine, oldest first; the scored run is appended
     *     automatically if it is not already the last element
     */
    public AutonScore score(Run run, Run baseline, List<Run> history) {
        List<Run> window = window(run, history);
        RunComparison comparison = baseline == null ? null : RunComparison.compare(baseline, run);
        return new AutonScore(run.id(), List.of(
                speed(run, baseline),
                accuracy(comparison),
                consistency(window),
                mechanisms(comparison),
                reliability(window)));
    }

    private MetricScore speed(Run run, Run baseline) {
        double weight = config.speedWeight();
        double target = !Double.isNaN(config.targetSeconds()) ? config.targetSeconds()
                : baseline != null ? baseline.duration() : Double.NaN;
        if (Double.isNaN(target)) {
            return MetricScore.unavailable("Speed", weight, "no target duration configured and no baseline");
        }
        if (run.status() != RunStatus.COMPLETED) {
            return new MetricScore("Speed", 0, weight, "run did not complete");
        }
        double limit = config.timeLimitSeconds();
        double score = target >= limit ? (run.duration() <= target ? 1 : 0)
                : new Falloff(target, limit).apply(run.duration());
        return new MetricScore("Speed", 100 * score, weight, String.format(Locale.ROOT,
                "%.2fs (full score ≤ %.2fs, zero at %.2fs)", run.duration(), target, limit));
    }

    private MetricScore accuracy(RunComparison comparison) {
        double weight = config.accuracyWeight();
        if (comparison == null) {
            return MetricScore.unavailable("Accuracy", weight, "no baseline");
        }
        List<Double> parts = new ArrayList<>();
        List<String> details = new ArrayList<>();
        double position = comparison.finalPositionError();
        if (!Double.isNaN(position)) {
            double heading = Math.abs(comparison.finalHeadingError());
            parts.add(config.position().apply(position));
            parts.add(config.heading().apply(heading));
            details.add(String.format(Locale.ROOT, "final error %.1fcm / %.1f°", position * 100,
                    Math.toDegrees(heading)));
        }
        if (!comparison.trajectory().isEmpty()) {
            double deviation = comparison.trajectory().meanPathDeviation();
            parts.add(config.position().apply(deviation));
            details.add(String.format(Locale.ROOT, "mean path deviation %.1fcm", deviation * 100));
        }
        if (parts.isEmpty()) {
            return MetricScore.unavailable("Accuracy", weight, "no poses recorded");
        }
        return new MetricScore("Accuracy", 100 * Stats.mean(parts), weight, String.join(", ", details));
    }

    private MetricScore consistency(List<Run> window) {
        double weight = config.consistencyWeight();
        if (window.size() < config.minConsistencyRuns()) {
            return MetricScore.unavailable("Consistency", weight, "needs at least " + config.minConsistencyRuns()
                    + " runs, have " + window.size());
        }
        double[] durations = window.stream().mapToDouble(Run::duration).toArray();
        double durationSpread = Stats.stdDev(durations);
        List<Double> parts = new ArrayList<>();
        parts.add(config.durationSpread().apply(durationSpread));
        String detail = String.format(Locale.ROOT, "%d runs, σ duration %.2fs", window.size(), durationSpread);

        List<Pose> ends = window.stream().map(Run::endPose).filter(p -> p != null).toList();
        if (ends.size() >= config.minConsistencyRuns()) {
            double cx = ends.stream().mapToDouble(Pose::x).average().orElse(0);
            double cy = ends.stream().mapToDouble(Pose::y).average().orElse(0);
            double positionSpread = Math.sqrt(ends.stream()
                    .mapToDouble(p -> (p.x() - cx) * (p.x() - cx) + (p.y() - cy) * (p.y() - cy)).average().orElse(0));
            double reference = ends.get(0).heading();
            double[] headingOffsets = ends.stream().mapToDouble(p -> Angles.difference(reference, p.heading()))
                    .toArray();
            double headingSpread = Stats.stdDev(headingOffsets);
            parts.add(config.position().apply(positionSpread));
            parts.add(config.heading().apply(headingSpread));
            detail += String.format(Locale.ROOT, ", end spread %.1fcm / %.1f°", positionSpread * 100,
                    Math.toDegrees(headingSpread));
        }
        return new MetricScore("Consistency", 100 * Stats.mean(parts), weight, detail);
    }

    private MetricScore mechanisms(RunComparison comparison) {
        double weight = config.mechanismsWeight();
        if (comparison == null) {
            return MetricScore.unavailable("Mechanisms", weight, "no baseline");
        }
        List<OccurrenceMatch> expected = comparison.events().stream()
                .filter(m -> m.status() != OccurrenceMatch.Status.EXTRA).toList();
        List<Double> parts = new ArrayList<>();
        List<String> details = new ArrayList<>();
        if (!expected.isEmpty()) {
            List<OccurrenceMatch> matched = expected.stream()
                    .filter(m -> m.status() == OccurrenceMatch.Status.MATCHED).toList();
            parts.add((double) matched.size() / expected.size());
            parts.add(orderScore(matched));
            details.add(matched.size() + "/" + expected.size() + " events");
        }
        Map<String, List<Double>> base = MechanismDurations.of(comparison.baseline(), s -> !idleStates.isIdle(s));
        Map<String, List<Double>> current = MechanismDurations.of(comparison.candidate(), s -> !idleStates.isIdle(s));
        List<Double> timing = new ArrayList<>();
        double worst = 0;
        for (Map.Entry<String, List<Double>> entry : base.entrySet()) {
            List<Double> other = current.getOrDefault(entry.getKey(), List.of());
            for (int i = 0; i < Math.min(other.size(), entry.getValue().size()); i++) {
                double difference = Math.abs(other.get(i) - entry.getValue().get(i));
                worst = Math.max(worst, difference);
                timing.add(config.timing().apply(difference));
            }
        }
        if (!timing.isEmpty()) {
            parts.add(Stats.mean(timing));
            details.add(String.format(Locale.ROOT, "worst state timing difference %.2fs", worst));
        }
        if (parts.isEmpty()) {
            return MetricScore.unavailable("Mechanisms", weight, "baseline has no events or mechanism states");
        }
        return new MetricScore("Mechanisms", 100 * Stats.mean(parts), weight, String.join(", ", details));
    }

    /** Fraction of consecutive expected events (in baseline order) that kept their order. */
    private static double orderScore(List<OccurrenceMatch> matched) {
        if (matched.size() < 2) {
            return matched.isEmpty() ? 0 : 1;
        }
        int ordered = 0;
        for (int i = 1; i < matched.size(); i++) {
            if (matched.get(i).candidateTime() >= matched.get(i - 1).candidateTime()) {
                ordered++;
            }
        }
        return (double) ordered / (matched.size() - 1);
    }

    private MetricScore reliability(List<Run> window) {
        long successes = window.stream().filter(Run::succeeded).count();
        return new MetricScore("Reliability", 100.0 * successes / window.size(), config.reliabilityWeight(),
                successes + "/" + window.size() + " runs completed without errors");
    }

    private List<Run> window(Run run, List<Run> history) {
        List<Run> all = new ArrayList<>(history == null ? List.of() : history);
        if (all.isEmpty() || !all.get(all.size() - 1).id().equals(run.id())) {
            all.add(run);
        }
        int from = Math.max(0, all.size() - config.historyWindow());
        return all.subList(from, all.size());
    }
}
