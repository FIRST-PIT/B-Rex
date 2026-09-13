package dev.brex.analysis.regression;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.MechanismDurations;
import dev.brex.analysis.Stats;
import dev.brex.analysis.Unit;
import dev.brex.analysis.compare.OccurrenceMatch;
import dev.brex.analysis.compare.RunComparison;
import dev.brex.analysis.compare.TrajectoryComparison;
import dev.brex.core.run.Run;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compares a candidate run with a known-good baseline and decides, check by check, whether the
 * routine regressed. See {@code docs/regression-testing.md} for the definition of every check.
 */
public final class RegressionDetector {

    /** Telemetry key holding the distance between estimated and reference pose, meters. */
    public static final String LOCALIZATION_ERROR_KEY = "localization.error";

    private final RegressionThresholds thresholds;
    private final IdleStates idleStates;

    public RegressionDetector(RegressionThresholds thresholds) {
        this(thresholds, IdleStates.DEFAULT);
    }

    public RegressionDetector(RegressionThresholds thresholds, IdleStates idleStates) {
        this.thresholds = thresholds;
        this.idleStates = idleStates;
    }

    public RegressionReport detect(Run baseline, Run candidate) {
        RunComparison comparison = RunComparison.compare(baseline, candidate);
        List<RegressionCheck> checks = new ArrayList<>();
        checks.add(duration(comparison));
        checks.addAll(trajectory(comparison));
        checks.addAll(finalPose(comparison));
        checks.add(localization(baseline, candidate));
        checks.add(missingEvents(comparison));
        checks.add(eventTiming(comparison));
        checks.add(mechanismTiming(baseline, candidate));
        checks.add(reliability(baseline, candidate));
        return new RegressionReport(candidate.name(), baseline.id(), candidate.id(), checks);
    }

    private RegressionCheck duration(RunComparison comparison) {
        double base = comparison.baseline().duration();
        double current = comparison.candidate().duration();
        double delta = current - base;
        boolean fail = delta > thresholds.maxDurationIncrease();
        String message = String.format(Locale.ROOT, "%s%.2fs %s than baseline", delta >= 0 ? "+" : "", delta,
                delta > 0 ? "slower" : "faster");
        return check("duration", "Duration", fail, base, current, thresholds.maxDurationIncrease(),
                Unit.SECONDS, message);
    }

    private List<RegressionCheck> trajectory(RunComparison comparison) {
        TrajectoryComparison trajectory = comparison.trajectory();
        if (trajectory.isEmpty()) {
            String reason = "trajectory not recorded in " + missingIn(comparison.baseline().trajectory().isEmpty(),
                    comparison.candidate().trajectory().isEmpty());
            return List.of(RegressionCheck.skip("path.mean", "Mean path deviation", reason),
                    RegressionCheck.skip("path.max", "Max path deviation", reason));
        }
        double mean = trajectory.meanPathDeviation();
        double max = trajectory.maxPathDeviation();
        return List.of(
                check("path.mean", "Mean path deviation", mean > thresholds.maxMeanPathDeviation(), 0, mean,
                        thresholds.maxMeanPathDeviation(), Unit.METERS,
                        String.format(Locale.ROOT, "%.1fcm average distance from baseline path", mean * 100)),
                check("path.max", "Max path deviation", max > thresholds.maxPathDeviation(), 0, max,
                        thresholds.maxPathDeviation(), Unit.METERS,
                        String.format(Locale.ROOT, "%.1fcm worst distance from baseline path", max * 100)));
    }

    private List<RegressionCheck> finalPose(RunComparison comparison) {
        double position = comparison.finalPositionError();
        if (Double.isNaN(position)) {
            String reason = "end pose not recorded in " + missingIn(comparison.baseline().endPose() == null,
                    comparison.candidate().endPose() == null);
            return List.of(RegressionCheck.skip("final.position", "Final position", reason),
                    RegressionCheck.skip("final.heading", "Final heading", reason));
        }
        double heading = Math.abs(comparison.finalHeadingError());
        return List.of(
                check("final.position", "Final position", position > thresholds.maxFinalPositionError(), 0,
                        position, thresholds.maxFinalPositionError(), Unit.METERS,
                        String.format(Locale.ROOT, "ended %.1fcm from baseline end position", position * 100)),
                check("final.heading", "Final heading", heading > thresholds.maxFinalHeadingError(), 0, heading,
                        thresholds.maxFinalHeadingError(), Unit.RADIANS,
                        String.format(Locale.ROOT, "ended %.1f° from baseline end heading", Math.toDegrees(heading))));
    }

    private RegressionCheck localization(Run baseline, Run candidate) {
        TelemetryChannel a = baseline.telemetry().channel(LOCALIZATION_ERROR_KEY);
        TelemetryChannel b = candidate.telemetry().channel(LOCALIZATION_ERROR_KEY);
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return RegressionCheck.skip("localization", "Localization",
                    "'" + LOCALIZATION_ERROR_KEY + "' not recorded in " + missingIn(a == null || a.isEmpty(),
                            b == null || b.isEmpty()));
        }
        double base = Stats.mean(a.numbers());
        double current = Stats.mean(b.numbers());
        double delta = current - base;
        return check("localization", "Localization", delta > thresholds.maxLocalizationErrorIncrease(), base,
                current, thresholds.maxLocalizationErrorIncrease(), Unit.METERS,
                String.format(Locale.ROOT, "%s%.1fcm average localization error", delta >= 0 ? "+" : "",
                        delta * 100));
    }

    private RegressionCheck missingEvents(RunComparison comparison) {
        List<OccurrenceMatch> missing = comparison.missingEvents();
        int expected = (int) comparison.events().stream()
                .filter(m -> m.status() != OccurrenceMatch.Status.EXTRA).count();
        if (expected == 0) {
            return RegressionCheck.skip("events.missing", "Expected events", "baseline recorded no events");
        }
        boolean fail = thresholds.requireBaselineEvents() && !missing.isEmpty();
        String message = missing.isEmpty() ? "all " + expected + " baseline events occurred"
                : "missing: " + missing.stream().map(OccurrenceMatch::label).collect(Collectors.joining(", "));
        return check("events.missing", "Expected events", fail, expected, expected - missing.size(), 0,
                Unit.COUNT, message);
    }

    private RegressionCheck eventTiming(RunComparison comparison) {
        OccurrenceMatch worst = null;
        for (OccurrenceMatch match : comparison.events()) {
            if (match.status() == OccurrenceMatch.Status.MATCHED && (worst == null || match.delta() > worst.delta())) {
                worst = match;
            }
        }
        if (worst == null) {
            return RegressionCheck.skip("events.timing", "Event timing", "no events occurred in both runs");
        }
        boolean fail = worst.delta() > thresholds.maxEventDelay();
        String message = String.format(Locale.ROOT, "%s %s%.2fs vs baseline", worst.label(),
                worst.delta() >= 0 ? "+" : "", worst.delta());
        return check("events.timing", "Event timing", fail, worst.baselineTime(), worst.candidateTime(),
                thresholds.maxEventDelay(), Unit.SECONDS, message);
    }

    /**
     * Compares how long each mechanism stayed in each active (non-idle) state occurrence. Unlike
     * event timing, this is not affected by delays earlier in the routine.
     */
    private RegressionCheck mechanismTiming(Run baseline, Run candidate) {
        Map<String, List<Double>> base = MechanismDurations.of(baseline, state -> !idleStates.isIdle(state));
        Map<String, List<Double>> current = MechanismDurations.of(candidate, state -> !idleStates.isIdle(state));
        String worstKey = null;
        int worstOccurrence = 0;
        double worstBase = Double.NaN;
        double worstCurrent = Double.NaN;
        for (Map.Entry<String, List<Double>> entry : base.entrySet()) {
            List<Double> other = current.get(entry.getKey());
            if (other == null) {
                continue;
            }
            for (int i = 0; i < Math.min(entry.getValue().size(), other.size()); i++) {
                double delta = other.get(i) - entry.getValue().get(i);
                if (worstKey == null || delta > worstCurrent - worstBase) {
                    worstKey = entry.getKey();
                    worstOccurrence = i;
                    worstBase = entry.getValue().get(i);
                    worstCurrent = other.get(i);
                }
            }
        }
        if (worstKey == null) {
            return RegressionCheck.skip("mechanisms.timing", "Mechanism timing",
                    "no mechanism states occurred in both runs");
        }
        double delta = worstCurrent - worstBase;
        String label = worstKey.replace(":", " ") + (worstOccurrence == 0 ? "" : " #" + (worstOccurrence + 1));
        String message = String.format(Locale.ROOT, "%s took %.2fs (baseline %.2fs, %s%.2fs)", label, worstCurrent,
                worstBase, delta >= 0 ? "+" : "", delta);
        return check("mechanisms.timing", "Mechanism timing", delta > thresholds.maxMechanismSlowdown(), worstBase,
                worstCurrent, thresholds.maxMechanismSlowdown(), Unit.SECONDS, message);
    }

    private RegressionCheck reliability(Run baseline, Run candidate) {
        int baseErrors = baseline.errors().size();
        int currentErrors = candidate.errors().size();
        boolean lostSuccess = baseline.succeeded() && !candidate.succeeded();
        boolean fail = lostSuccess || currentErrors > baseErrors;
        String message;
        if (lostSuccess) {
            message = "run " + candidate.status().name().toLowerCase(Locale.ROOT)
                    + (currentErrors > 0 ? " with " + currentErrors + " error(s)" : "")
                    + "; baseline completed cleanly";
        } else if (currentErrors > baseErrors) {
            message = currentErrors + " error(s), baseline had " + baseErrors;
        } else {
            message = candidate.status().name().toLowerCase(Locale.ROOT) + ", " + currentErrors + " error(s), "
                    + candidate.warnings().size() + " warning(s)";
        }
        return check("reliability", "Reliability", fail, baseErrors, currentErrors, 0, Unit.COUNT, message);
    }

    private static RegressionCheck check(String id, String title, boolean fail, double baseline, double current,
            double threshold, Unit unit, String message) {
        return new RegressionCheck(id, title, fail ? RegressionCheck.Status.FAIL : RegressionCheck.Status.PASS,
                baseline, current, threshold, unit, message);
    }

    private static String missingIn(boolean baselineMissing, boolean candidateMissing) {
        if (baselineMissing && candidateMissing) {
            return "either run";
        }
        return baselineMissing ? "baseline" : "current run";
    }
}
