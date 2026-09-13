package dev.brex.testing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.brex.analysis.regression.RegressionThresholds;
import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunStatus;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RunAssertionsTest {

    static Run.Builder blueLeft(String id) {
        return Run.builder(id, RunMetadata.builder("blue-left").build())
                .duration(22.31)
                .trajectory(new Trajectory.Builder()
                        .add(0, Pose.ORIGIN)
                        .add(10, Pose.of(1, 0, Math.toRadians(45)))
                        .add(22.31, Pose.of(1, 1, Math.toRadians(90)))
                        .build())
                .event(Event.of(3.2, "alignment.complete"))
                .event(Event.of(8.0, "deposit.start"))
                .event(Event.of(9.1, "deposit.complete", Map.of("points", "4")))
                .event(Event.of(15.0, "deposit.complete", Map.of("points", "2")))
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .mechanismState(MechanismStateChange.of(7.0, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(7.8, "lift", "HIGH"))
                .telemetry(new Telemetry(List.of(
                        TelemetryChannel.numeric("lift.position", new double[] {0, 7.8, 20},
                                new double[] {0, 1200, 0}),
                        TelemetryChannel.numeric("localization.error", new double[] {0, 10, 20},
                                new double[] {0.004, 0.021, 0.012}))));
    }

    private final Run run = blueLeft("20260913-175012-blue-left-3fa9").build();

    private static String failure(Executable assertion) {
        return assertThrows(BrexAssertionError.class, assertion).getMessage();
    }

    @Test
    void duration() {
        assertDoesNotThrow(() -> RunAssertions.assertTimeBelow(run, 25.0));
        assertEquals("Expected blue-left to finish in under 20.00s, but it took 22.31s "
                + "[run 20260913-175012-blue-left-3fa9]", failure(() -> RunAssertions.assertTimeBelow(run, 20.0)));
    }

    @Test
    void timeBelowRequiresCompletion() {
        Run incomplete = blueLeft("r").status(RunStatus.INCOMPLETE).build();

        assertEquals("Expected blue-left to complete, but it ended incomplete [run r]",
                failure(() -> RunAssertions.assertTimeBelow(incomplete, 25.0)));
    }

    @Test
    void errorsAndWarnings() {
        Run failing = blueLeft("r").log(LogEntry.of(4.2, LogLevel.ERROR, "lift stalled")).build();

        assertDoesNotThrow(() -> RunAssertions.assertNoErrors(run));
        assertEquals("Expected blue-left to log no errors, but it logged 1: \"lift stalled\" at 4.20s [run r]",
                failure(() -> RunAssertions.assertNoErrors(failing)));
    }

    @Test
    void endPose() {
        assertDoesNotThrow(() -> RunAssertions.assertEndPoseNear(run, Pose.of(1.01, 1, Math.toRadians(89)), 0.02, 2));
        assertEquals("Expected blue-left to end within 2.0cm of (100.0cm, 105.0cm), but it ended 5.0cm away at "
                + "(100.0cm, 100.0cm) [run " + run.id() + "]",
                failure(() -> RunAssertions.assertEndPositionNear(run, Pose.of(1, 1.05, 0), 0.02)));
        assertEquals("Expected blue-left to end facing -90.0° ± 5.0°, but it ended facing 90.0° (180.0° off) [run "
                + run.id() + "]", failure(() -> RunAssertions.assertEndHeadingNear(run, -90, 5)));
    }

    @Test
    void positionAtTimeAndDistance() {
        assertDoesNotThrow(() -> RunAssertions.assertPositionAt(run, 5, Pose.of(0.5, 0, 0), 0.001));
        assertDoesNotThrow(() -> RunAssertions.assertDistanceBelow(run, 2.01));
        assertThrows(BrexAssertionError.class, () -> RunAssertions.assertDistanceBelow(run, 1.5));
    }

    @Test
    void missingTrajectoryExplainsHowToRecordIt() {
        Run noPoses = Run.builder("r", RunMetadata.builder("blue-left").build()).build();

        assertEquals("Expected blue-left to have an end pose, but no poses were recorded (record them with "
                + "brex.trajectory) [run r]", failure(() -> RunAssertions.assertEndHeadingNear(noPoses, 0, 1)));
    }

    @Test
    void localizationError() {
        assertDoesNotThrow(() -> RunAssertions.assertLocalizationErrorBelow(run, 0.05));
        assertEquals("Expected blue-left to keep localization error below 2.0cm, but it reached 2.1cm at 10.00s "
                + "[run " + run.id() + "]", failure(() -> RunAssertions.assertLocalizationErrorBelow(run, 0.02)));
    }

    @Test
    void pathDeviationAgainstBaseline() {
        Run baseline = blueLeft("41").build();
        Run shifted = blueLeft("42").trajectory(new Trajectory.Builder()
                .add(0, Pose.of(0, 0.03, 0)).add(10, Pose.of(1, 0.03, 0)).build()).build();

        assertDoesNotThrow(() -> RunAssertions.assertPathDeviationBelow(run, baseline, 0.001));
        assertThrows(BrexAssertionError.class, () -> RunAssertions.assertPathDeviationBelow(shifted, baseline, 0.02));
    }

    @Test
    void eventOccurrence() {
        assertDoesNotThrow(() -> RunAssertions.assertEventOccurred(run, "alignment.complete"));
        assertDoesNotThrow(() -> RunAssertions.assertEventCount(run, "deposit.complete", 2));
        assertEquals("Expected blue-left to record event 'park', but it never happened (recorded: "
                + "alignment.complete, deposit.start, deposit.complete) [run " + run.id() + "]",
                failure(() -> RunAssertions.assertEventOccurred(run, "park")));
        assertEquals("Expected blue-left never to record event 'deposit.start', but it happened at 8.00s [run "
                + run.id() + "]", failure(() -> RunAssertions.assertEventNotOccurred(run, "deposit.start")));
    }

    @Test
    void eventOrderingAndTiming() {
        assertDoesNotThrow(() -> RunAssertions.assertEventOrder(run, "alignment.complete", "deposit.start",
                "deposit.complete"));
        assertDoesNotThrow(() -> RunAssertions.assertElapsedBetween(run, "deposit.start", "deposit.complete", 1.5));
        assertDoesNotThrow(() -> RunAssertions.assertEventWithin(run, "alignment.complete", 5));
        assertEquals("Expected blue-left to record 'deposit.start' before 'alignment.complete', but they happened "
                + "at 8.00s and 3.20s [run " + run.id() + "]",
                failure(() -> RunAssertions.assertEventBefore(run, "deposit.start", "alignment.complete")));
        assertEquals("Expected blue-left to go from 'deposit.start' to 'deposit.complete' in at most 1.00s, but it "
                + "took 1.10s (8.00s -> 9.10s) [run " + run.id() + "]",
                failure(() -> RunAssertions.assertElapsedBetween(run, "deposit.start", "deposit.complete", 1.0)));
    }

    @Test
    void mechanismStates() {
        assertDoesNotThrow(() -> RunAssertions.assertMechanismStateAt(run, "lift", "RAISING", 7.5));
        assertDoesNotThrow(() -> RunAssertions.assertFinalMechanismState(run, "lift", "HIGH"));
        assertDoesNotThrow(() -> RunAssertions.assertStateDurationBelow(run, "lift", "RAISING", 1.0));
        assertEquals("Expected blue-left to keep lift in RAISING for at most 0.50s, but occurrence #1 lasted 0.80s "
                + "[run " + run.id() + "]",
                failure(() -> RunAssertions.assertStateDurationBelow(run, "lift", "RAISING", 0.5)));
        assertEquals("Expected blue-left to put lift in state LOW, but it only entered IDLE, RAISING, HIGH [run "
                + run.id() + "]", failure(() -> RunAssertions.assertMechanismReached(run, "lift", "LOW")));
        assertEquals("Expected blue-left to record states for mechanism 'arm', but it has none (recorded: lift) [run "
                + run.id() + "]", failure(() -> RunAssertions.assertMechanismReached(run, "arm", "UP")));
    }

    @Test
    void telemetry() {
        assertDoesNotThrow(() -> RunAssertions.assertTelemetryBelow(run, "lift.position", 1300));
        assertDoesNotThrow(() -> RunAssertions.assertTelemetryReached(run, "lift.position", 1200));
        assertDoesNotThrow(() -> RunAssertions.assertTelemetryAt(run, "lift.position", 10, 1200, 1));
        assertDoesNotThrow(() -> RunAssertions.assertFinalTelemetry(run, "lift.position", 0, 1));
        assertEquals("Expected blue-left to reach lift.position = 1500, but the highest value was 1200 [run "
                + run.id() + "]", failure(() -> RunAssertions.assertTelemetryReached(run, "lift.position", 1500)));
        assertEquals("No telemetry recorded for 'lift.pos' (recorded keys include: [lift.position]) [run " + run.id()
                + "]", failure(() -> RunAssertions.assertTelemetryBelow(run, "lift.pos", 1)));
    }

    @Test
    void gamePoints() {
        assertDoesNotThrow(() -> RunAssertions.assertScores(run, 6));
        assertEquals("Expected blue-left to score 8 points, but it scored 6 (deposit.complete 4 @9.1s, "
                + "deposit.complete 2 @15.0s) [run " + run.id() + "]",
                failure(() -> RunAssertions.assertScores(run, 8)));
    }

    @Test
    void reliability() {
        Run failed = blueLeft("bad").status(RunStatus.FAILED).build();

        assertDoesNotThrow(() -> RunAssertions.assertReliabilityAtLeast(List.of(run, run, failed), 60));
        assertEquals("Expected blue-left to succeed in at least 90.0% of runs, but only 2/3 (66.7%) succeeded. "
                + "Failed runs: bad", failure(() -> RunAssertions.assertReliabilityAtLeast(List.of(run, run, failed),
                        90)));
    }

    @Test
    void noRegression() {
        Run baseline = blueLeft("41").build();
        Run slower = blueLeft("42").duration(23.15).build();

        assertDoesNotThrow(() -> RunAssertions.assertNoRegression(run, baseline, RegressionThresholds.DEFAULTS));
        assertEquals("Expected blue-left not to regress from baseline 41, but: +0.84s slower than baseline [run 42]",
                failure(() -> RunAssertions.assertNoRegression(slower, baseline, RegressionThresholds.DEFAULTS)));
    }
}
