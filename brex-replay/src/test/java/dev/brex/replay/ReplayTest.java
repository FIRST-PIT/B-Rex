package dev.brex.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunKind;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplayTest {

    private static Run match() {
        return Run.builder("match-038", RunMetadata.builder("match-038").kind(RunKind.MATCH).build())
                .duration(158)
                .trajectory(new Trajectory.Builder().add(0, Pose.ORIGIN).add(10, Pose.of(1, 0, 0)).build())
                .phase(MatchPhase.of("autonomous", 0, 30))
                .phase(MatchPhase.of("teleop", 38, 158))
                .event(Event.of(2, "intake.start"))
                .event(Event.of(5, "deposit.complete", Map.of("points", "8")))
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .mechanismState(MechanismStateChange.of(4, "lift", "RAISING"))
                .log(LogEntry.of(4.5, LogLevel.WARNING, "lift current high"))
                .telemetry(new Telemetry(List.of(
                        TelemetryChannel.numeric("lift.position", new double[] {0, 4, 6}, new double[] {0, 400, 1200}),
                        TelemetryChannel.text("auto.state", new double[] {0, 3}, new String[] {"DRIVE", "SCORE"}))))
                .build();
    }

    @Test
    void timelineMergesEverythingChronologically() {
        Timeline timeline = Timeline.of(match());

        List<String> subjects = timeline.entries().stream().map(e -> e.kind() + ":" + e.subject()).toList();

        assertEquals(List.of("PHASE_START:autonomous", "MECHANISM_STATE:lift", "EVENT:intake.start",
                "MECHANISM_STATE:lift", "LOG:WARNING", "EVENT:deposit.complete", "PHASE_END:autonomous",
                "PHASE_START:teleop", "PHASE_END:teleop"), subjects);
    }

    @Test
    void timelineRecordsPreviousMechanismState() {
        TimelineEntry raising = Timeline.of(match()).entries().stream()
                .filter(e -> e.kind() == TimelineEntry.Kind.MECHANISM_STATE && e.detail().equals("RAISING"))
                .findFirst().orElseThrow();

        assertEquals("IDLE", raising.previous());
        assertEquals("points=8", Timeline.of(match()).between(5, 5).get(0).detail());
    }

    @Test
    void timelineCanBeFiltered() {
        Timeline events = Timeline.of(match()).only(Set.of(TimelineEntry.Kind.EVENT));

        assertEquals(2, events.size());
    }

    @Test
    void snapshotReconstructsStateAtTime() {
        RobotSnapshot snapshot = new ReplaySession(match()).seek(5).snapshot();

        assertEquals(0.5, snapshot.pose().x(), 1e-9);
        assertEquals("RAISING", snapshot.mechanismStates().get("lift"));
        assertEquals(400.0, snapshot.numbers().get("lift.position"));
        assertEquals("SCORE", snapshot.texts().get("auto.state"));
        assertEquals("deposit.complete", snapshot.lastEvent().name());
        assertEquals("autonomous", snapshot.phase());
    }

    @Test
    void snapshotBeforeAnythingHappened() {
        Run run = Run.builder("r", RunMetadata.builder("blue-left").build()).duration(5)
                .event(Event.of(1, "start")).build();

        RobotSnapshot snapshot = new ReplaySession(run).snapshot();

        assertNull(snapshot.pose());
        assertNull(snapshot.lastEvent());
        assertTrue(snapshot.mechanismStates().isEmpty());
    }

    @Test
    void cursorClampsAndStepsThroughEntries() {
        ReplaySession session = new ReplaySession(match());

        assertEquals(158.0, session.seek(1000).time());
        assertEquals(0.0, session.seek(-1).time());
        assertEquals("intake.start", session.next().subject());
        assertEquals(2.0, session.time());
        session.step(100);
        assertEquals(102.0, session.time());
    }

    @Test
    void nextReturnsNullAtEnd() {
        ReplaySession session = new ReplaySession(match()).seek(158);

        assertNull(session.next());
        assertTrue(session.atEnd());
    }

    @Test
    void seeksToPhase() {
        ReplaySession session = new ReplaySession(match()).seekPhase("teleop");

        assertEquals(38.0, session.time());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> session.seekPhase("endgame"));
        assertEquals("Run match-038 has no phase 'endgame' (phases: autonomous, teleop)", error.getMessage());
    }

    @Test
    void ghostReplayAlignsRunsOnSharedClock() {
        Run ghost = Run.builder("41", RunMetadata.builder("blue-left").build())
                .trajectory(new Trajectory.Builder().add(0, Pose.ORIGIN).add(2, Pose.of(2, 0, 0)).build()).build();
        Run current = Run.builder("42", RunMetadata.builder("blue-left").build())
                .trajectory(new Trajectory.Builder().add(0, Pose.ORIGIN).add(4, Pose.of(2, 0, 0)).build()).build();

        GhostReplay replay = new GhostReplay(current, ghost);
        GhostReplay.Frame frame = replay.frameAt(1);

        assertEquals(4.0, replay.duration());
        assertEquals(0.5, frame.positionError(), 1e-9);
        assertEquals(0.0, replay.frameAt(4).positionError(), 1e-9);
        assertEquals(5, replay.frames(1.0).size());
        assertEquals(4, replay.frames(1.5).size());
    }
}
