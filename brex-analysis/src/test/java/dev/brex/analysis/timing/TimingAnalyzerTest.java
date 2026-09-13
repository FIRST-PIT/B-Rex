package dev.brex.analysis.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.TestRuns;
import dev.brex.analysis.timing.TimingReport.IdlePeriod;
import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimingAnalyzerTest {

    private final TimingAnalyzer analyzer = new TimingAnalyzer(TimingOptions.DEFAULTS);

    /**
     * Drive 0-3s, wait 3-4s, intake 4-5s, wait 5-5.5s, lift 5.5-7s while driving 6-8s, idle 8-10s.
     */
    private static Run routine() {
        return Run.builder("r", RunMetadata.builder("blue-left").build())
                .duration(10)
                .trajectory(TestRuns.sampled(10, t -> {
                    double x = t < 3 ? t / 3 : t < 6 ? 1 : t < 8 ? 1 + (t - 6) / 2 : 2;
                    return Pose.of(x, 0, 0);
                }))
                .event(Event.of(3, "alignment.complete"))
                .mechanismState(MechanismStateChange.of(0, "intake", "IDLE"))
                .mechanismState(MechanismStateChange.of(4, "intake", "RUNNING"))
                .mechanismState(MechanismStateChange.of(5, "intake", "IDLE"))
                .mechanismState(MechanismStateChange.of(5.5, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(7, "lift", "IDLE"))
                .build();
    }

    @Test
    void buildsDriveAndMechanismLanes() {
        TimingReport report = analyzer.analyze(routine());

        assertEquals(List.of("drive", "intake", "lift"), report.lanes().stream().map(TimingReport.Lane::name).toList());
        TimingReport.Lane drive = report.lanes().get(0);
        assertEquals(2, drive.intervals().size());
        assertEquals(0.0, drive.intervals().get(0).start(), 0.03);
        assertEquals(3.0, drive.intervals().get(0).end(), 0.03);
        assertEquals(5.0, drive.activeTime(), 0.1);
        assertEquals(1.0, report.lanes().get(1).activeTime(), 1e-9);
    }

    @Test
    void findsIdlePeriodsWithContext() {
        TimingReport report = analyzer.analyze(routine());

        List<IdlePeriod> idle = report.idlePeriods();
        assertEquals(3, idle.size(), idle.toString());
        IdlePeriod first = idle.get(0);
        assertEquals(3.0, first.start(), 0.03);
        assertEquals(4.0, first.end(), 1e-9);
        assertEquals("drive", first.before());
        assertEquals("intake RUNNING", first.after());
        assertEquals("alignment.complete", first.lastEvent());
        assertEquals(0.5, idle.get(1).duration(), 1e-9);
        assertEquals(2.0, idle.get(2).duration(), 0.03);
        assertEquals(null, idle.get(2).after());
        assertEquals(3.5, report.totalIdle(), 0.05);
    }

    @Test
    void shortPausesAreNotIdle() {
        TimingReport report = new TimingAnalyzer(new TimingOptions(0.02, Math.toRadians(5), 0.6, 0.1,
                dev.brex.analysis.IdleStates.DEFAULT)).analyze(routine());

        assertEquals(2, report.idlePeriods().size());
    }

    @Test
    void reportsOverlappingWork() {
        TimingReport report = analyzer.analyze(routine());

        TimingReport.Overlap overlap = report.overlaps().get(0);
        assertEquals("drive", overlap.laneA());
        assertEquals("lift", overlap.laneB());
        assertEquals(1.0, overlap.seconds(), 0.05);
    }

    @Test
    void listsLongestStatesWithBaseline() {
        Run baseline = Run.builder("b", RunMetadata.builder("blue-left").build()).duration(10)
                .mechanismState(MechanismStateChange.of(5, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(6, "lift", "IDLE"))
                .build();

        TimingReport report = analyzer.analyze(routine(), baseline);

        TimingReport.StateTiming longest = report.longestStates().get(0);
        assertEquals("lift", longest.mechanism());
        assertEquals(1.5, longest.duration(), 1e-9);
        assertEquals(1.0, longest.baselineDuration(), 1e-9);
        assertTrue(Double.isNaN(report.longestStates().get(1).baselineDuration()));
    }

    @Test
    void detectsRepeatedCycles() {
        Run.Builder cycles = Run.builder("c", RunMetadata.builder("blue-left").build()).duration(20);
        double[] cycleStarts = {1, 6.2, 11.7};
        for (double s : cycleStarts) {
            cycles.mechanismState(MechanismStateChange.of(s, "intake", "RUNNING"))
                    .mechanismState(MechanismStateChange.of(s + 1, "intake", "IDLE"))
                    .mechanismState(MechanismStateChange.of(s + 1.5, "lift", "RAISING"))
                    .mechanismState(MechanismStateChange.of(s + 2.5, "lift", "IDLE"))
                    .mechanismState(MechanismStateChange.of(s + 3, "claw", "OPEN"))
                    .mechanismState(MechanismStateChange.of(s + 3.4, "claw", "CLOSED"));
        }

        TimingReport.Cycle cycle = analyzer.analyze(cycles.build()).cycle().orElseThrow();

        assertEquals(List.of("intake:RUNNING", "lift:RAISING", "claw:OPEN", "claw:CLOSED"), cycle.steps());
        assertEquals(3, cycle.repetitions());
        assertEquals(5.2, cycle.durations().get(0), 1e-9);
        assertEquals(5.5, cycle.durations().get(1), 1e-9);
        assertEquals(8.3, cycle.durations().get(2), 1e-9);
    }

    @Test
    void runWithoutDataHasNoFindings() {
        TimingReport report = analyzer.analyze(Run.builder("e", RunMetadata.builder("x").build()).duration(0).build());

        assertTrue(report.idlePeriods().isEmpty());
        assertTrue(report.cycle().isEmpty());
        assertTrue(report.overlaps().isEmpty());
    }
}
