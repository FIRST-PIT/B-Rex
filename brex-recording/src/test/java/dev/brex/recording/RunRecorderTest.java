package dev.brex.recording;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.LogLevel;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunKind;
import dev.brex.core.run.RunStatus;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.time.ManualClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RunRecorderTest {

    private final ManualClock clock = new ManualClock(1000);

    private RunRecorder start(String name) {
        return Brex.builder(name).clock(clock).startedAtEpochMillis(0).random(new Random(7)).start();
    }

    @Test
    void recordsTelemetryRelativeToStart() {
        RunRecorder brex = start("blue-left");
        brex.telemetry.record("lift.position", 0);
        clock.advance(0.5);
        brex.telemetry.record("lift.position", 1200);

        Run run = brex.complete();

        assertArrayEquals(new double[] {0, 0.5}, run.telemetry().require("lift.position").times());
        assertArrayEquals(new double[] {0, 1200}, run.telemetry().require("lift.position").numbers());
    }

    @Test
    void recordsTypedTelemetry() {
        RunRecorder brex = start("blue-left");
        brex.telemetry.record("intake.hasSample", true);
        brex.telemetry.record("auto.state", RunStatus.COMPLETED);

        Run run = brex.complete();

        assertEquals(ChannelType.BOOLEAN, run.telemetry().require("intake.hasSample").type());
        assertEquals("COMPLETED", run.telemetry().require("auto.state").text(0));
    }

    @Test
    void typeMismatchBecomesWarningInsteadOfException() {
        RunRecorder brex = start("blue-left");
        brex.telemetry.record("lift.position", 10);
        brex.telemetry.record("lift.position", "high");
        brex.telemetry.record("lift.position", "high");

        Run run = brex.complete();

        assertEquals(1, run.telemetry().require("lift.position").size());
        assertEquals(1, run.warnings().size());
        assertTrue(run.warnings().get(0).message().startsWith("B-rex: Telemetry key 'lift.position'"));
    }

    @Test
    void invalidNamesNeverThrow() {
        RunRecorder brex = start("blue-left");
        brex.telemetry.record("bad key", 1);
        brex.event("bad event");
        brex.mechanism("bad mechanism", "IDLE");
        brex.phase("bad phase");

        Run run = brex.complete();

        assertTrue(run.events().isEmpty());
        assertEquals(4, run.warnings().size());
    }

    @Test
    void recordsTrajectoryAndEvents() {
        RunRecorder brex = start("blue-left");
        brex.startPose(Pose.ofInches(-36, 63, -90));
        brex.trajectory.recordInches(-36, 63, -90);
        clock.advance(1.25);
        brex.event("alignment.complete");
        brex.trajectory.record(Pose.of(0, 1, 0));
        brex.score("sample.high", 8);

        Run run = brex.complete();

        assertEquals(2, run.trajectory().size());
        assertEquals(Pose.of(0, 1, 0), run.endPose());
        assertEquals(1.25, run.firstEvent("alignment.complete").time());
        assertEquals(8.0, run.pointsScored());
        assertEquals(-90, run.startPose().headingDegrees(), 1e-9);
    }

    @Test
    void storesOnlyMechanismStateChanges() {
        RunRecorder brex = start("blue-left");
        brex.mechanism("lift", "IDLE");
        clock.advance(0.1);
        brex.mechanism("lift", "IDLE");
        clock.advance(0.1);
        brex.mechanism("lift", RunKind.MATCH);

        Run run = brex.complete();

        assertEquals(2, run.mechanismStates().size());
        assertEquals("IDLE", run.mechanismStateAt("lift", 0.15));
        assertEquals("MATCH", run.mechanismStateAt("lift", 0.25));
    }

    @Test
    void stopWithoutCompleteIsIncomplete() {
        RunRecorder brex = start("blue-left");
        clock.advance(30);

        Run run = brex.stop();

        assertEquals(RunStatus.INCOMPLETE, run.status());
        assertEquals(30.0, run.duration());
    }

    @Test
    void failureIsRecordedAndSticky() {
        RunRecorder brex = start("blue-left");
        brex.fail(new IllegalStateException("lift stalled"));

        Run run = brex.complete();

        assertEquals(RunStatus.FAILED, run.status());
        assertEquals("IllegalStateException: lift stalled", run.errors().get(0).message());
    }

    @Test
    void stopIsIdempotentAndIgnoresLaterSamples() {
        List<Run> stopped = new ArrayList<>();
        RunRecorder brex = Brex.builder("blue-left").clock(clock).onStop(stopped::add).start();
        clock.advance(2);
        Run first = brex.complete();
        clock.advance(5);
        brex.event("late");
        brex.telemetry.record("late", 1);

        assertSame(first, brex.stop());
        assertEquals(1, stopped.size());
        assertEquals(2.0, first.duration());
        assertFalse(brex.snapshot().hasEvent("late"));
        assertEquals(2.0, brex.elapsed());
    }

    @Test
    void recordsMatchPhases() {
        RunRecorder brex = Brex.builder("match-038").kind(RunKind.MATCH).clock(clock).start();
        brex.phase(MatchPhase.AUTONOMOUS);
        clock.advance(30);
        brex.phase(MatchPhase.TRANSITION);
        clock.advance(8);
        brex.phase(MatchPhase.TELEOP);
        clock.advance(120);

        Run run = brex.complete();

        assertEquals(RunKind.MATCH, run.metadata().kind());
        assertEquals(List.of(MatchPhase.of("autonomous", 0, 30), MatchPhase.of("transition", 30, 38),
                MatchPhase.of("teleop", 38, 158)), run.phases());
    }

    @Test
    void snapshotIncludesOpenPhaseWithoutStopping() {
        RunRecorder brex = start("blue-left");
        brex.phase("drive");
        clock.advance(3);

        Run snapshot = brex.snapshot();

        assertTrue(brex.isRecording());
        assertEquals(3.0, snapshot.phase("drive").end());
    }

    @Test
    void capsSamplesPerChannel() {
        RunRecorder brex = Brex.builder("blue-left").clock(clock).maxSamplesPerChannel(3).start();
        for (int i = 0; i < 10; i++) {
            brex.telemetry.record("x", i);
        }

        Run run = brex.complete();

        assertEquals(3, run.telemetry().require("x").size());
        assertEquals(LogLevel.WARNING, run.log().get(0).level());
    }

    @Test
    void tracksCurrentRecorder() {
        RunRecorder brex = start("blue-left");

        assertSame(brex, Brex.current());
        brex.stop();
        assertNull(Brex.current());
    }

    @Test
    void generatesIdFromNameAndStartTime() {
        RunRecorder brex = start("Blue_Left");

        assertTrue(brex.id().startsWith("19700101-000000-blue_left-"), brex.id());
    }

    @Test
    void invalidRunNameFailsFastAtStart() {
        // Unlike samples, a bad routine name is a configuration error caught the first time the
        // OpMode is initialized, long before a match.
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> start("Blue Left"));

        assertEquals("Run name 'Blue Left' must not contain whitespace", error.getMessage());
    }
}
