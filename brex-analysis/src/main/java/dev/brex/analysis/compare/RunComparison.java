package dev.brex.analysis.compare;

import dev.brex.analysis.Stats;
import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A ghost-run comparison of a candidate run against a baseline run of the same routine.
 *
 * <p>Everything is exposed as raw data (per-sample trajectory errors and matched event/state
 * times) so that the CLI can summarize it and a visualization can render both runs together.
 */
public final class RunComparison {

    private final Run baseline;
    private final Run candidate;
    private final TrajectoryComparison trajectory;
    private final List<OccurrenceMatch> events;
    private final List<OccurrenceMatch> mechanismStates;

    private RunComparison(Run baseline, Run candidate, TrajectoryComparison trajectory) {
        this.baseline = baseline;
        this.candidate = candidate;
        this.trajectory = trajectory;
        this.events = matchOccurrences(baseline.events(), candidate.events(), Event::name, Event::time);
        this.mechanismStates = matchOccurrences(baseline.mechanismStates(), candidate.mechanismStates(),
                c -> c.mechanism() + ":" + c.state(), MechanismStateChange::time);
    }

    public static RunComparison compare(Run baseline, Run candidate) {
        return compare(baseline, candidate, TrajectoryComparison.Options.DEFAULT);
    }

    public static RunComparison compare(Run baseline, Run candidate, TrajectoryComparison.Options options) {
        return new RunComparison(baseline, candidate,
                TrajectoryComparison.compare(baseline.trajectory(), candidate.trajectory(), options));
    }

    public Run baseline() {
        return baseline;
    }

    public Run candidate() {
        return candidate;
    }

    /** Candidate duration minus baseline duration, seconds. Positive means slower. */
    public double durationDelta() {
        return candidate.duration() - baseline.duration();
    }

    public TrajectoryComparison trajectory() {
        return trajectory;
    }

    /** Distance between the two end poses, meters, or NaN when either run has no pose. */
    public double finalPositionError() {
        Pose a = baseline.endPose();
        Pose b = candidate.endPose();
        return a == null || b == null ? Double.NaN : a.distanceTo(b);
    }

    /** Signed heading difference between end poses (candidate minus baseline), radians. */
    public double finalHeadingError() {
        Pose a = baseline.endPose();
        Pose b = candidate.endPose();
        return a == null || b == null ? Double.NaN : a.headingDifferenceTo(b);
    }

    /** Event occurrences matched by name and occurrence index. */
    public List<OccurrenceMatch> events() {
        return events;
    }

    /** Mechanism state entries matched by mechanism, state and occurrence index. */
    public List<OccurrenceMatch> mechanismStates() {
        return mechanismStates;
    }

    /** Baseline event occurrences that the candidate never reached. */
    public List<OccurrenceMatch> missingEvents() {
        return events.stream().filter(m -> m.status() == OccurrenceMatch.Status.MISSING).toList();
    }

    /**
     * Mechanism states of both runs at the same moment, for every mechanism seen in either run.
     * Values are {@code [baselineState, candidateState]}; either may be null.
     */
    public Map<String, String[]> mechanismStatesAt(double time) {
        Map<String, String[]> result = new LinkedHashMap<>();
        for (String mechanism : baseline.mechanismNames()) {
            result.put(mechanism, new String[2]);
        }
        for (String mechanism : candidate.mechanismNames()) {
            result.putIfAbsent(mechanism, new String[2]);
        }
        for (Map.Entry<String, String[]> entry : result.entrySet()) {
            entry.getValue()[0] = baseline.mechanismStateAt(entry.getKey(), time);
            entry.getValue()[1] = candidate.mechanismStateAt(entry.getKey(), time);
        }
        return result;
    }

    /**
     * Mean absolute time-aligned difference of a numeric telemetry key, evaluated at the
     * candidate's sample times. NaN when either run lacks the key.
     */
    public double telemetryDifference(String key) {
        TelemetryChannel a = baseline.telemetry().channel(key);
        TelemetryChannel b = candidate.telemetry().channel(key);
        if (a == null || b == null || a.type() == ChannelType.TEXT || b.type() == ChannelType.TEXT) {
            return Double.NaN;
        }
        double[] diffs = new double[b.size()];
        for (int i = 0; i < diffs.length; i++) {
            diffs[i] = Math.abs(b.number(i) - a.numberAt(b.time(i)));
        }
        return Stats.mean(diffs);
    }

    private static <T> List<OccurrenceMatch> matchOccurrences(List<T> baselineItems, List<T> candidateItems,
            Function<T, String> key, Function<T, Double> time) {
        Map<String, List<Double>> baselineTimes = groupTimes(baselineItems, key, time);
        Map<String, List<Double>> candidateTimes = groupTimes(candidateItems, key, time);
        List<OccurrenceMatch> matches = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : baselineTimes.entrySet()) {
            List<Double> other = candidateTimes.getOrDefault(entry.getKey(), List.of());
            for (int i = 0; i < entry.getValue().size(); i++) {
                matches.add(new OccurrenceMatch(entry.getKey(), i, entry.getValue().get(i),
                        i < other.size() ? other.get(i) : Double.NaN));
            }
        }
        for (Map.Entry<String, List<Double>> entry : candidateTimes.entrySet()) {
            int baselineCount = baselineTimes.getOrDefault(entry.getKey(), List.of()).size();
            for (int i = baselineCount; i < entry.getValue().size(); i++) {
                matches.add(new OccurrenceMatch(entry.getKey(), i, Double.NaN, entry.getValue().get(i)));
            }
        }
        matches.sort((a, b) -> Double.compare(firstKnown(a), firstKnown(b)));
        return List.copyOf(matches);
    }

    private static double firstKnown(OccurrenceMatch match) {
        return Double.isNaN(match.baselineTime()) ? match.candidateTime() : match.baselineTime();
    }

    private static <T> Map<String, List<Double>> groupTimes(List<T> items, Function<T, String> key,
            Function<T, Double> time) {
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            grouped.computeIfAbsent(key.apply(item), k -> new ArrayList<>()).add(time.apply(item));
        }
        return grouped;
    }
}
