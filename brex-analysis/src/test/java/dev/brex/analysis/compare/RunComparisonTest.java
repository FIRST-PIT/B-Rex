package dev.brex.analysis.compare;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.TestRuns;
import dev.brex.core.event.Event;
import dev.brex.core.run.Run;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunComparisonTest {

    @Test
    void reportsDurationAndEventTimingDeltas() {
        Run baseline = TestRuns.cycle("41", 1.0, 0).build();
        Run candidate = TestRuns.cycle("42", 1.1, 0).build();

        RunComparison comparison = RunComparison.compare(baseline, candidate);

        assertEquals(1.0, comparison.durationDelta(), 1e-9);
        OccurrenceMatch deposit = comparison.events().stream()
                .filter(m -> m.key().equals("deposit.complete")).findFirst().orElseThrow();
        assertEquals(OccurrenceMatch.Status.MATCHED, deposit.status());
        assertEquals(0.8, deposit.delta(), 1e-9);
    }

    @Test
    void detectsMissingAndExtraEvents() {
        Run baseline = TestRuns.cycle("41", 1.0, 0).build();
        Run candidate = TestRuns.cycle("42", 1.0, 0)
                .events(List.of(Event.of(9, "deposit.start"))) // a second deposit attempt
                .build()
                .toBuilder().build();
        Run withoutDeposit = Run.builder("43", baseline.metadata())
                .events(baseline.events().stream().filter(e -> !e.name().equals("deposit.complete")).toList())
                .build();

        RunComparison extra = RunComparison.compare(baseline, candidate);
        RunComparison missing = RunComparison.compare(baseline, withoutDeposit);

        assertTrue(extra.events().stream().anyMatch(m -> m.status() == OccurrenceMatch.Status.EXTRA
                && m.label().equals("deposit.start #2")));
        assertEquals(List.of("deposit.complete"), missing.missingEvents().stream().map(OccurrenceMatch::key).toList());
    }

    @Test
    void matchesMechanismStatesByOccurrence() {
        RunComparison comparison = RunComparison.compare(TestRuns.cycle("41", 1.0, 0).build(),
                TestRuns.cycle("42", 1.2, 0).build());

        OccurrenceMatch raising = comparison.mechanismStates().stream()
                .filter(m -> m.key().equals("lift:RAISING")).findFirst().orElseThrow();

        assertEquals(1.0, raising.delta(), 1e-9);
        // Baseline raises the lift at 5s, the slower candidate at 6s.
        assertArrayEquals(new String[] {"RAISING", "IDLE"}, comparison.mechanismStatesAt(5.5).get("lift"));
    }

    @Test
    void reportsFinalPoseError() {
        RunComparison comparison = RunComparison.compare(TestRuns.cycle("41", 1.0, 0).build(),
                TestRuns.cycle("42", 1.0, 0.05).build());

        assertEquals(0.05, comparison.finalPositionError(), 1e-9);
        assertEquals(0.0, comparison.finalHeadingError(), 1e-9);
    }

    @Test
    void comparesNumericTelemetry() {
        Telemetry a = new Telemetry(List.of(TelemetryChannel.numeric("lift.position", new double[] {0, 1},
                new double[] {0, 1000})));
        Telemetry b = new Telemetry(List.of(TelemetryChannel.numeric("lift.position", new double[] {0, 1},
                new double[] {10, 1030})));
        Run baseline = TestRuns.cycle("41", 1, 0).telemetry(a).build();
        Run candidate = TestRuns.cycle("42", 1, 0).telemetry(b).build();

        RunComparison comparison = RunComparison.compare(baseline, candidate);

        assertEquals(20.0, comparison.telemetryDifference("lift.position"), 1e-9);
        assertTrue(Double.isNaN(comparison.telemetryDifference("missing")));
    }
}
