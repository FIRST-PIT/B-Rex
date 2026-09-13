package dev.brex.analysis.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.TestRuns;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutonScorerTest {

    private final AutonScorer scorer = new AutonScorer(ScoringConfig.DEFAULTS);
    private final Run baseline = TestRuns.cycle("41", 1.0, 0).build();

    @Test
    void falloffIsLinearBetweenFullAndZero() {
        Falloff falloff = new Falloff(1, 3);

        assertEquals(1.0, falloff.apply(0.5));
        assertEquals(0.5, falloff.apply(2));
        assertEquals(0.0, falloff.apply(10));
        assertThrows(IllegalArgumentException.class, () -> new Falloff(3, 1));
    }

    @Test
    void runMatchingBaselineScoresPerfectOnComparableMetrics() {
        AutonScore score = scorer.score(TestRuns.cycle("42", 1.0, 0).build(), baseline, List.of());

        assertEquals(100.0, score.metric("Speed").score(), 1e-9);
        assertEquals(100.0, score.metric("Accuracy").score(), 1e-9);
        assertEquals(100.0, score.metric("Mechanisms").score(), 1e-9);
        assertEquals(100.0, score.metric("Reliability").score(), 1e-9);
        assertFalse(score.metric("Consistency").available());
        assertEquals(100.0, score.total(), 1e-9);
    }

    @Test
    void speedFallsOffLinearlyTowardsTimeLimit() {
        // Baseline 10s, limit 30s: a 15s run is a quarter of the way to zero.
        Run slower = TestRuns.cycle("42", 1.5, 0).build();

        assertEquals(75.0, scorer.score(slower, baseline, List.of()).metric("Speed").score(), 1e-9);
    }

    @Test
    void incompleteRunScoresZeroSpeed() {
        Run incomplete = TestRuns.cycle("42", 1.0, 0).status(RunStatus.INCOMPLETE).build();

        AutonScore score = scorer.score(incomplete, baseline, List.of());

        assertEquals(0.0, score.metric("Speed").score());
        assertEquals(0.0, score.metric("Reliability").score());
    }

    @Test
    void accuracyUsesFinalPoseAndPathDeviation() {
        // 5.5cm offset everywhere: position curve 1cm..10cm gives 0.5 for both position parts;
        // heading is unaffected (1.0). Mean = (0.5 + 1.0 + 0.5) / 3.
        AutonScore score = scorer.score(TestRuns.cycle("42", 1.0, 0.055).build(), baseline, List.of());

        assertEquals(100 * 2.0 / 3.0, score.metric("Accuracy").score(), 1e-6);
    }

    @Test
    void consistencyNeedsEnoughRuns() {
        List<Run> history = List.of(TestRuns.cycle("1", 1.0, 0).build(), TestRuns.cycle("2", 1.0, 0).build());

        AutonScore score = scorer.score(TestRuns.cycle("3", 1.0, 0).build(), baseline, history);

        assertEquals(100.0, score.metric("Consistency").score(), 1e-9);
    }

    @Test
    void consistencyPenalizesSpread() {
        // Durations 10, 10, 14.5 -> σ ≈ 2.12s (duration part 0); end positions differ by up to 11cm.
        List<Run> history = List.of(TestRuns.cycle("1", 1.0, 0).build(), TestRuns.cycle("2", 1.0, 0.11).build());

        AutonScore score = scorer.score(TestRuns.cycle("3", 1.45, 0).build(), baseline, history);

        assertTrue(score.metric("Consistency").score() < 60, score.metric("Consistency").detail());
    }

    @Test
    void reliabilityIsSuccessRateOverWindow() {
        Run failed = TestRuns.cycle("2", 1.0, 0).log(LogEntry.of(1, LogLevel.ERROR, "stall")).build();
        List<Run> history = List.of(TestRuns.cycle("0", 1.0, 0).build(), TestRuns.cycle("1", 1.0, 0).build(), failed);

        AutonScore score = scorer.score(TestRuns.cycle("3", 1.0, 0).build(), baseline, history);

        assertEquals(75.0, score.metric("Reliability").score(), 1e-9);
    }

    @Test
    void mechanismsScoreDropsWhenEventsAreMissing() {
        Run candidate = Run.builder("42", baseline.metadata()).duration(10)
                .events(baseline.events().subList(0, 2))
                .mechanismStates(baseline.mechanismStates())
                .build();

        MetricScore mechanisms = scorer.score(candidate, baseline, List.of()).metric("Mechanisms");

        // presence 2/4, order 1, timing 1 -> 83.3
        assertEquals(100 * (0.5 + 1 + 1) / 3, mechanisms.score(), 1e-6);
        assertEquals("2/4 events, worst state timing difference 0.00s", mechanisms.detail());
    }

    @Test
    void unavailableMetricsAreExcludedFromTotal() {
        AutonScore score = scorer.score(TestRuns.cycle("42", 1.0, 0).build(), null, List.of());

        assertEquals(List.of("Speed", "Accuracy", "Consistency", "Mechanisms"),
                score.unavailable().stream().map(MetricScore::name).toList());
        assertEquals(100.0, score.total(), 1e-9);
    }

    @Test
    void weightsAreConfigurable() {
        ScoringConfig speedOnly = ScoringConfig.DEFAULTS.withWeights(1, 0, 0, 0, 0);
        Run slower = TestRuns.cycle("42", 1.5, 0).build();

        assertEquals(75.0, new AutonScorer(speedOnly).score(slower, baseline, List.of()).total(), 1e-9);
    }

    @Test
    void configuredTargetOverridesBaseline() {
        ScoringConfig config = ScoringConfig.DEFAULTS.withTargetSeconds(20);

        AutonScore score = new AutonScorer(config).score(TestRuns.cycle("42", 2.5, 0).build(), null, List.of());

        assertEquals(50.0, score.metric("Speed").score(), 1e-9);
    }

    @Test
    void scoringIsDeterministic() {
        Run candidate = TestRuns.cycle("42", 1.13, 0.021).build();

        assertEquals(scorer.score(candidate, baseline, List.of()), scorer.score(candidate, baseline, List.of()));
    }
}
