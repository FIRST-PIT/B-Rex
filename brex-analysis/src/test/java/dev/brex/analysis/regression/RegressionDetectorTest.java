package dev.brex.analysis.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.TestRuns;
import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunStatus;
import dev.brex.core.run.TestResult;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegressionDetectorTest {

    private final RegressionDetector detector = new RegressionDetector(RegressionThresholds.DEFAULTS);
    private final Run baseline = TestRuns.cycle("41", 1.0, 0).build();

    @Test
    void identicalRunPassesEveryCheck() {
        RegressionReport report = detector.detect(baseline, TestRuns.cycle("42", 1.0, 0).build());

        assertTrue(report.passed(), () -> report.failures().toString());
        assertEquals(RegressionCheck.Status.SKIP, report.check("localization").status());
    }

    @Test
    void detectsSlowerRun() {
        RegressionReport report = detector.detect(baseline, TestRuns.cycle("42", 1.084, 0).build());

        RegressionCheck duration = report.check("duration");
        assertFalse(report.passed());
        assertEquals(RegressionCheck.Status.FAIL, duration.status());
        assertEquals(0.84, duration.delta(), 1e-9);
        assertEquals("+0.84s slower than baseline", duration.message());
    }

    @Test
    void smallSlowdownWithinThresholdPasses() {
        RegressionReport report = detector.detect(baseline, TestRuns.cycle("42", 1.02, 0).build());

        assertEquals(RegressionCheck.Status.PASS, report.check("duration").status());
    }

    @Test
    void detectsLessAccurateTrajectoryAndFinalPose() {
        RegressionReport report = detector.detect(baseline, TestRuns.cycle("42", 1.0, 0.05).build());

        assertEquals(RegressionCheck.Status.FAIL, report.check("path.mean").status());
        assertEquals(RegressionCheck.Status.FAIL, report.check("final.position").status());
        assertEquals(RegressionCheck.Status.PASS, report.check("final.heading").status());
    }

    @Test
    void detectsMissingEvents() {
        Run candidate = Run.builder("42", baseline.metadata())
                .events(baseline.events().stream().filter(e -> !e.name().equals("deposit.complete")).toList())
                .build();

        RegressionCheck check = detector.detect(baseline, candidate).check("events.missing");

        assertEquals(RegressionCheck.Status.FAIL, check.status());
        assertEquals("missing: deposit.complete", check.message());
    }

    @Test
    void uniformDelayFailsEventTimingButNotMechanismTiming() {
        RegressionReport report = detector.detect(baseline, shifted(baseline, 1.0));

        assertEquals(RegressionCheck.Status.FAIL, report.check("events.timing").status());
        assertEquals(RegressionCheck.Status.PASS, report.check("mechanisms.timing").status());
    }

    @Test
    void detectsSlowMechanismTransition() {
        // The lift stays in RAISING 1.4s longer; nothing else changes.
        Run.Builder slowLift = Run.builder("43", baseline.metadata()).duration(baseline.duration())
                .trajectory(baseline.trajectory()).events(baseline.events());
        for (MechanismStateChange change : baseline.mechanismStates()) {
            boolean liftLowered = change.mechanism().equals("lift") && change.state().equals("IDLE") && change.time() > 0;
            slowLift.mechanismState(liftLowered
                    ? MechanismStateChange.of(change.time() + 1.4, "lift", "IDLE") : change);
        }

        RegressionCheck check = detector.detect(baseline, slowLift.build()).check("mechanisms.timing");

        assertEquals(RegressionCheck.Status.FAIL, check.status());
        assertEquals("lift RAISING took 2.40s (baseline 1.00s, +1.40s)", check.message());
    }

    @Test
    void finalMechanismStateDoesNotCountAsSlowdown() {
        // The lift ends the routine RAISING; the candidate simply records for longer.
        Run.Builder base = Run.builder("41", baseline.metadata()).duration(10)
                .mechanismState(MechanismStateChange.of(5, "lift", "RAISING"));
        Run.Builder longer = Run.builder("42", baseline.metadata()).duration(12)
                .mechanismState(MechanismStateChange.of(5, "lift", "RAISING"));

        RegressionCheck check = detector.detect(base.build(), longer.build()).check("mechanisms.timing");

        assertEquals(RegressionCheck.Status.SKIP, check.status());
    }

    @Test
    void detectsWorseLocalization() {
        Run base = baseline.toBuilder().telemetry(localizationError(0.01)).build();
        Run worse = TestRuns.cycle("42", 1.0, 0).telemetry(localizationError(0.031)).build();

        RegressionCheck check = detector.detect(base, worse).check("localization");

        assertEquals(RegressionCheck.Status.FAIL, check.status());
        assertEquals("+2.1cm average localization error", check.message());
    }

    @Test
    void detectsReliabilityDecrease() {
        Run failed = TestRuns.cycle("42", 1.0, 0).status(RunStatus.FAILED)
                .log(LogEntry.of(3, LogLevel.ERROR, "lift stalled")).build();

        RegressionCheck check = detector.detect(baseline, failed).check("reliability");

        assertEquals(RegressionCheck.Status.FAIL, check.status());
        assertEquals("run failed with 1 error(s); baseline completed cleanly", check.message());
    }

    @Test
    void thresholdsAreConfigurable() {
        RegressionThresholds lenient = new RegressionThresholds(2.0, 1, 1, 1, 1, 5, 5, 1, false);

        RegressionReport report = new RegressionDetector(lenient).detect(baseline,
                TestRuns.cycle("42", 1.084, 0.05).build());

        assertTrue(report.passed(), () -> report.failures().toString());
    }

    @Test
    void convertsToTestResults() {
        List<TestResult> results = detector.detect(baseline, TestRuns.cycle("42", 1.1, 0).build()).toTestResults();

        assertTrue(results.stream().anyMatch(r -> r.name().equals("regression.duration")
                && r.status() == TestResult.Status.FAILED));
    }

    private static Telemetry localizationError(double meters) {
        return new Telemetry(List.of(TelemetryChannel.numeric("localization.error", new double[] {0, 5},
                new double[] {meters, meters})));
    }

    /** The same run with every event and state change (after t=0) delayed by {@code seconds}. */
    private static Run shifted(Run run, double seconds) {
        Run.Builder b = Run.builder(run.id() + "-shifted", run.metadata())
                .duration(run.duration() + seconds)
                .trajectory(run.trajectory());
        run.events().forEach(e -> b.event(Event.of(e.time() + seconds, e.name())));
        run.mechanismStates().forEach(c -> b.mechanismState(MechanismStateChange.of(
                c.time() == 0 ? 0 : c.time() + seconds, c.mechanism(), c.state())));
        return b.build();
    }
}
