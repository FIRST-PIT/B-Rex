package dev.brex.testing;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.MechanismDurations;
import dev.brex.analysis.compare.TrajectoryComparison;
import dev.brex.analysis.regression.RegressionCheck;
import dev.brex.analysis.regression.RegressionDetector;
import dev.brex.analysis.regression.RegressionReport;
import dev.brex.analysis.regression.RegressionThresholds;
import dev.brex.analysis.score.AutonScore;
import dev.brex.analysis.score.AutonScorer;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunStatus;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Assertions about recorded runs. Every failure message names the routine, the expectation, the
 * actual value and the run ID, so a failing CI log is enough to start debugging.
 *
 * <p>Distances are meters and angles are degrees unless the method name says otherwise.
 * Extend {@link AutonomousTest} to call these without passing the run each time.
 */
public final class RunAssertions {

    private RunAssertions() {
    }

    // ---------------------------------------------------------------- completion and duration

    /** The routine signalled completion. */
    public static void assertCompleted(Run run) {
        if (run.status() != RunStatus.COMPLETED) {
            fail(run, "to complete, but it ended " + run.status().name().toLowerCase(Locale.ROOT)
                    + errorsSuffix(run));
        }
    }

    /** The routine completed in less than {@code seconds}. */
    public static void assertTimeBelow(Run run, double seconds) {
        assertCompleted(run);
        if (!(run.duration() < seconds)) {
            fail(run, format("to finish in under %.2fs, but it took %.2fs", seconds, run.duration()));
        }
    }

    /** The run's duration is within {@code [min, max]} seconds. */
    public static void assertDurationBetween(Run run, double min, double max) {
        if (run.duration() < min || run.duration() > max) {
            fail(run, format("to take between %.2fs and %.2fs, but it took %.2fs", min, max, run.duration()));
        }
    }

    /** No error was logged during the run. */
    public static void assertNoErrors(Run run) {
        if (!run.errors().isEmpty()) {
            fail(run, "to log no errors, but it logged " + run.errors().size() + ": " + describeLog(run.errors()));
        }
    }

    /** No warning was logged during the run. */
    public static void assertNoWarnings(Run run) {
        if (!run.warnings().isEmpty()) {
            fail(run, "to log no warnings, but it logged " + run.warnings().size() + ": "
                    + describeLog(run.warnings()));
        }
    }

    // ---------------------------------------------------------------- pose, heading, distance

    /** The final position is within {@code toleranceMeters} of {@code expected}. */
    public static void assertEndPositionNear(Run run, Pose expected, double toleranceMeters) {
        Pose end = requireEndPose(run);
        double error = end.distanceTo(expected);
        if (error > toleranceMeters) {
            fail(run, format("to end within %.1fcm of (%.1fcm, %.1fcm), but it ended %.1fcm away at (%.1fcm, %.1fcm)",
                    toleranceMeters * 100, expected.x() * 100, expected.y() * 100, error * 100, end.x() * 100,
                    end.y() * 100));
        }
    }

    /** The final heading is within {@code toleranceDegrees} of {@code expectedDegrees}. */
    public static void assertEndHeadingNear(Run run, double expectedDegrees, double toleranceDegrees) {
        Pose end = requireEndPose(run);
        double error = Math.toDegrees(Math.abs(Pose.of(0, 0, Math.toRadians(expectedDegrees))
                .headingDifferenceTo(end)));
        if (error > toleranceDegrees) {
            fail(run, format("to end facing %.1f° ± %.1f°, but it ended facing %.1f° (%.1f° off)", expectedDegrees,
                    toleranceDegrees, end.headingDegrees(), error));
        }
    }

    /** The final pose is within both tolerances of {@code expected}. */
    public static void assertEndPoseNear(Run run, Pose expected, double toleranceMeters, double toleranceDegrees) {
        assertEndPositionNear(run, expected, toleranceMeters);
        assertEndHeadingNear(run, expected.headingDegrees(), toleranceDegrees);
    }

    /** The pose at {@code time} is within {@code toleranceMeters} of {@code expected}. */
    public static void assertPositionAt(Run run, double time, Pose expected, double toleranceMeters) {
        requireTrajectory(run);
        Pose actual = run.trajectory().poseAt(time);
        double error = actual.distanceTo(expected);
        if (error > toleranceMeters) {
            fail(run, format("to be within %.1fcm of (%.1fcm, %.1fcm) at %.2fs, but it was %.1fcm away",
                    toleranceMeters * 100, expected.x() * 100, expected.y() * 100, time, error * 100));
        }
    }

    /** The robot travelled less than {@code meters} in total. */
    public static void assertDistanceBelow(Run run, double meters) {
        requireTrajectory(run);
        double distance = run.trajectory().pathLength();
        if (!(distance < meters)) {
            fail(run, format("to travel less than %.2fm, but it travelled %.2fm", meters, distance));
        }
    }

    /** The recorded {@code localization.error} never exceeded {@code meters}. */
    public static void assertLocalizationErrorBelow(Run run, double meters) {
        TelemetryChannel channel = requireNumeric(run, RegressionDetector.LOCALIZATION_ERROR_KEY);
        double worst = Double.NEGATIVE_INFINITY;
        double at = 0;
        for (int i = 0; i < channel.size(); i++) {
            if (channel.number(i) > worst) {
                worst = channel.number(i);
                at = channel.time(i);
            }
        }
        if (!(worst < meters)) {
            fail(run, format("to keep localization error below %.1fcm, but it reached %.1fcm at %.2fs", meters * 100,
                    worst * 100, at));
        }
    }

    /** The trajectory never strayed more than {@code meters} from the baseline's path. */
    public static void assertPathDeviationBelow(Run run, Run baseline, double meters) {
        requireTrajectory(run);
        requireTrajectory(baseline);
        TrajectoryComparison comparison = TrajectoryComparison.compare(baseline.trajectory(), run.trajectory());
        double worst = comparison.maxPathDeviation();
        if (!(worst < meters)) {
            double at = comparison.samples().stream().filter(s -> s.pathDeviation() == worst).findFirst()
                    .map(s -> s.time()).orElse(Double.NaN);
            fail(run, format("to stay within %.1fcm of the baseline path (run %s), but it strayed %.1fcm at %.2fs",
                    meters * 100, baseline.id(), worst * 100, at));
        }
    }

    // ---------------------------------------------------------------- events

    public static void assertEventOccurred(Run run, String event) {
        if (!run.hasEvent(event)) {
            fail(run, "to record event '" + event + "', but it never happened" + eventsSuffix(run));
        }
    }

    public static void assertEventNotOccurred(Run run, String event) {
        Event first = run.firstEvent(event);
        if (first != null) {
            fail(run, format("never to record event '%s', but it happened at %.2fs", event, first.time()));
        }
    }

    public static void assertEventCount(Run run, String event, int expected) {
        int count = run.events(event).size();
        if (count != expected) {
            fail(run, "to record event '" + event + "' " + expected + " time(s), but it happened " + count
                    + " time(s)");
        }
    }

    /** The first {@code first} event happened before the first {@code second} event. */
    public static void assertEventBefore(Run run, String first, String second) {
        Event a = requireEvent(run, first);
        Event b = requireEvent(run, second);
        if (!(a.time() < b.time())) {
            fail(run, format("to record '%s' before '%s', but they happened at %.2fs and %.2fs", first, second,
                    a.time(), b.time()));
        }
    }

    /** The events happened in exactly this relative order (by first occurrence). */
    public static void assertEventOrder(Run run, String... events) {
        for (int i = 1; i < events.length; i++) {
            assertEventBefore(run, events[i - 1], events[i]);
        }
    }

    /** The first {@code event} happened within {@code seconds} of the start of the run. */
    public static void assertEventWithin(Run run, String event, double seconds) {
        Event e = requireEvent(run, event);
        if (e.time() > seconds) {
            fail(run, format("to record '%s' within %.2fs, but it happened at %.2fs", event, seconds, e.time()));
        }
    }

    /**
     * The first {@code to} event after the first {@code from} event happened at most
     * {@code seconds} later. Example: {@code assertElapsedBetween(run, "deposit.start",
     * "deposit.complete", 1.5)}.
     */
    public static void assertElapsedBetween(Run run, String from, String to, double seconds) {
        Event start = requireEvent(run, from);
        Event end = run.events(to).stream().filter(e -> e.time() >= start.time()).findFirst().orElse(null);
        if (end == null) {
            fail(run, format("to record '%s' after '%s' (at %.2fs), but it never happened", to, from, start.time()));
            return;
        }
        double elapsed = end.time() - start.time();
        if (elapsed > seconds) {
            fail(run, format("to go from '%s' to '%s' in at most %.2fs, but it took %.2fs (%.2fs -> %.2fs)", from, to,
                    seconds, elapsed, start.time(), end.time()));
        }
    }

    // ---------------------------------------------------------------- mechanisms

    /** The mechanism was in {@code state} at {@code time}. */
    public static void assertMechanismStateAt(Run run, String mechanism, String state, double time) {
        requireMechanism(run, mechanism);
        String actual = run.mechanismStateAt(mechanism, time);
        if (!state.equals(actual)) {
            fail(run, format("to have %s in state %s at %.2fs, but it was %s", mechanism, state, time,
                    actual == null ? "not yet in any state" : actual));
        }
    }

    /** The mechanism entered {@code state} at least once. */
    public static void assertMechanismReached(Run run, String mechanism, String state) {
        requireMechanism(run, mechanism);
        boolean reached = run.mechanismStates(mechanism).stream().anyMatch(c -> c.state().equals(state));
        if (!reached) {
            fail(run, "to put " + mechanism + " in state " + state + ", but it only entered "
                    + run.mechanismStates(mechanism).stream().map(c -> c.state()).distinct()
                            .collect(Collectors.joining(", ")));
        }
    }

    /** The mechanism ended the run in {@code state}. */
    public static void assertFinalMechanismState(Run run, String mechanism, String state) {
        assertMechanismStateAt(run, mechanism, state, run.duration());
    }

    /** Every occurrence of {@code state} lasted at most {@code seconds}. */
    public static void assertStateDurationBelow(Run run, String mechanism, String state, double seconds) {
        requireMechanism(run, mechanism);
        List<Double> durations = MechanismDurations.of(run).getOrDefault(mechanism + ":" + state, List.of());
        if (durations.isEmpty()) {
            fail(run, "to put " + mechanism + " in state " + state + ", but it never entered it");
        }
        for (int i = 0; i < durations.size(); i++) {
            if (durations.get(i) > seconds) {
                fail(run, format("to keep %s in %s for at most %.2fs, but occurrence #%d lasted %.2fs", mechanism,
                        state, seconds, i + 1, durations.get(i)));
            }
        }
    }

    // ---------------------------------------------------------------- telemetry

    /** Every sample of a numeric key stayed below {@code max}. */
    public static void assertTelemetryBelow(Run run, String key, double max) {
        TelemetryChannel channel = requireNumeric(run, key);
        for (int i = 0; i < channel.size(); i++) {
            if (!(channel.number(i) < max)) {
                fail(run, format("to keep %s below %s, but it was %s at %.2fs", key, num(max),
                        num(channel.number(i)), channel.time(i)));
            }
        }
    }

    /** Every sample of a numeric key stayed above {@code min}. */
    public static void assertTelemetryAbove(Run run, String key, double min) {
        TelemetryChannel channel = requireNumeric(run, key);
        for (int i = 0; i < channel.size(); i++) {
            if (!(channel.number(i) > min)) {
                fail(run, format("to keep %s above %s, but it was %s at %.2fs", key, num(min),
                        num(channel.number(i)), channel.time(i)));
            }
        }
    }

    /** Some sample of a numeric key reached at least {@code value}. */
    public static void assertTelemetryReached(Run run, String key, double value) {
        TelemetryChannel channel = requireNumeric(run, key);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < channel.size(); i++) {
            if (channel.number(i) >= value) {
                return;
            }
            max = Math.max(max, channel.number(i));
        }
        fail(run, "to reach " + key + " = " + num(value) + ", but the highest value was " + num(max));
    }

    /** The value of a numeric key at {@code time} is within {@code tolerance} of {@code expected}. */
    public static void assertTelemetryAt(Run run, String key, double time, double expected, double tolerance) {
        TelemetryChannel channel = requireNumeric(run, key);
        double actual = channel.numberAt(time);
        if (Double.isNaN(actual) || Math.abs(actual - expected) > tolerance) {
            fail(run, format("to have %s = %s ± %s at %.2fs, but it was %s", key, num(expected), num(tolerance), time,
                    Double.isNaN(actual) ? "not yet recorded" : num(actual)));
        }
    }

    /** The last recorded value of a numeric key is within {@code tolerance} of {@code expected}. */
    public static void assertFinalTelemetry(Run run, String key, double expected, double tolerance) {
        assertTelemetryAt(run, key, Double.POSITIVE_INFINITY, expected, tolerance);
    }

    // ---------------------------------------------------------------- scoring and reliability

    /** Game points recorded with {@code score(...)} add up to exactly {@code points}. */
    public static void assertScores(Run run, double points) {
        double actual = run.pointsScored();
        if (actual != points) {
            fail(run, "to score " + num(points) + " points, but it scored " + num(actual) + pointsSuffix(run));
        }
    }

    /** Game points add up to at least {@code points}. */
    public static void assertScoresAtLeast(Run run, double points) {
        double actual = run.pointsScored();
        if (actual < points) {
            fail(run, "to score at least " + num(points) + " points, but it scored " + num(actual) + pointsSuffix(run));
        }
    }

    /** The autonomous score total is at least {@code minimum}. */
    public static void assertAutonScoreAtLeast(Run run, Run baseline, List<Run> history, ScoringConfig config,
            double minimum) {
        AutonScore score = new AutonScorer(config, IdleStates.DEFAULT).score(run, baseline, history);
        if (Double.isNaN(score.total()) || score.total() < minimum) {
            String breakdown = score.metrics().stream()
                    .map(m -> m.name() + " " + (m.available() ? format("%.1f", m.score()) : "n/a"))
                    .collect(Collectors.joining(", "));
            fail(run, format("to score at least %.1f, but it scored %.1f (%s)", minimum, score.total(), breakdown));
        }
    }

    /** At least {@code percent}% of the runs completed without errors. */
    public static void assertReliabilityAtLeast(List<Run> runs, double percent) {
        if (runs.isEmpty()) {
            throw new BrexAssertionError("Expected runs to check reliability, but the list is empty");
        }
        long ok = runs.stream().filter(Run::succeeded).count();
        double actual = 100.0 * ok / runs.size();
        if (actual < percent) {
            String failures = runs.stream().filter(r -> !r.succeeded()).map(Run::id).limit(5)
                    .collect(Collectors.joining(", "));
            throw new BrexAssertionError(format("Expected %s to succeed in at least %.1f%% of runs, but only %d/%d "
                    + "(%.1f%%) succeeded. Failed runs: %s", runs.get(0).name(), percent, ok, runs.size(), actual,
                    failures));
        }
    }

    /** The run shows no regression against the baseline under the given thresholds. */
    public static void assertNoRegression(Run run, Run baseline, RegressionThresholds thresholds) {
        RegressionReport report = new RegressionDetector(thresholds).detect(baseline, run);
        if (!report.passed()) {
            String failures = report.failures().stream().map(RegressionCheck::message)
                    .collect(Collectors.joining("; "));
            fail(run, "not to regress from baseline " + baseline.id() + ", but: " + failures);
        }
    }

    /** Fails with a message built like the built-in assertions. */
    public static void fail(Run run, String expectation) {
        throw new BrexAssertionError("Expected " + run.name() + " " + expectation + " [run " + run.id() + "]");
    }

    // ---------------------------------------------------------------- helpers

    private static Pose requireEndPose(Run run) {
        Pose end = run.endPose();
        if (end == null) {
            fail(run, "to have an end pose, but no poses were recorded (record them with brex.trajectory)");
        }
        return end;
    }

    private static void requireTrajectory(Run run) {
        if (run.trajectory().isEmpty()) {
            fail(run, "to have a trajectory, but no poses were recorded (record them with brex.trajectory)");
        }
    }

    private static Event requireEvent(Run run, String event) {
        Event e = run.firstEvent(event);
        if (e == null) {
            fail(run, "to record event '" + event + "', but it never happened" + eventsSuffix(run));
        }
        return e;
    }

    private static void requireMechanism(Run run, String mechanism) {
        if (!run.mechanismNames().contains(mechanism)) {
            fail(run, "to record states for mechanism '" + mechanism + "', but it has none"
                    + (run.mechanismNames().isEmpty() ? "" : " (recorded: " + String.join(", ", run.mechanismNames())
                            + ")"));
        }
    }

    private static TelemetryChannel requireNumeric(Run run, String key) {
        TelemetryChannel channel;
        try {
            channel = run.telemetry().require(key);
        } catch (IllegalArgumentException e) {
            throw new BrexAssertionError(e.getMessage() + " [run " + run.id() + "]");
        }
        if (channel.type() == ChannelType.TEXT) {
            fail(run, "to record numbers for " + key + ", but it holds text");
        }
        if (channel.isEmpty()) {
            fail(run, "to record samples for " + key + ", but it has none");
        }
        return channel;
    }

    private static String eventsSuffix(Run run) {
        List<String> names = run.eventNames();
        if (names.isEmpty()) {
            return " (the run recorded no events)";
        }
        return " (recorded: " + String.join(", ", names.subList(0, Math.min(8, names.size())))
                + (names.size() > 8 ? ", ..." : "") + ")";
    }

    private static String errorsSuffix(Run run) {
        return run.errors().isEmpty() ? "" : " with error: " + run.errors().get(0).message();
    }

    private static String pointsSuffix(Run run) {
        List<String> scoring = run.events().stream().filter(e -> e.points() != 0)
                .map(e -> format("%s %s @%.1fs", e.name(), num(e.points()), e.time())).toList();
        return scoring.isEmpty() ? " (no scoring events recorded)" : " (" + String.join(", ", scoring) + ")";
    }

    private static String describeLog(List<LogEntry> entries) {
        return entries.stream().limit(3).map(e -> format("\"%s\" at %.2fs", e.message(), e.time()))
                .collect(Collectors.joining(", ")) + (entries.size() > 3 ? ", ..." : "");
    }

    private static String num(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e12 ? Long.toString((long) value)
                : format("%.3f", value);
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
