package dev.brex.core.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.trajectory.Trajectory;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RunTest {

    private static Run.Builder blueLeft() {
        return Run.builder("run-1", RunMetadata.builder("blue-left").build());
    }

    @Test
    void sortsEventsChronologicallyKeepingRecordingOrderForTies() {
        Run run = blueLeft()
                .event(Event.of(2.0, "deposit.start"))
                .event(Event.of(1.0, "intake.start"))
                .event(Event.of(1.0, "intake.stop"))
                .build();

        assertEquals(List.of("intake.start", "intake.stop", "deposit.start"), run.eventNames());
    }

    @Test
    void infersDurationFromLatestTimestamp() {
        Run run = blueLeft()
                .trajectory(new Trajectory.Builder().add(0, Pose.ORIGIN).add(4.0, Pose.of(1, 0, 0)).build())
                .event(Event.of(5.5, "park"))
                .build();

        assertEquals(5.5, run.duration());
    }

    @Test
    void explicitDurationWins() {
        assertEquals(22.31, blueLeft().duration(22.31).event(Event.of(1, "x")).build().duration());
    }

    @Test
    void posesFallBackToTrajectory() {
        Trajectory trajectory = new Trajectory.Builder().add(0, Pose.of(1, 1, 0)).add(1, Pose.of(2, 2, 0)).build();

        Run withTrajectory = blueLeft().trajectory(trajectory).build();
        Run explicit = blueLeft().trajectory(trajectory).startPose(Pose.ORIGIN).build();

        assertEquals(Pose.of(1, 1, 0), withTrajectory.startPose());
        assertEquals(Pose.of(2, 2, 0), withTrajectory.endPose());
        assertEquals(Pose.ORIGIN, explicit.startPose());
        assertNull(blueLeft().build().startPose());
    }

    @Test
    void tracksMechanismStateOverTime() {
        Run run = blueLeft()
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .mechanismState(MechanismStateChange.of(1, "intake", "RUNNING"))
                .mechanismState(MechanismStateChange.of(2, "lift", "RAISING"))
                .build();

        assertNull(run.mechanismStateAt("lift", -1));
        assertEquals("IDLE", run.mechanismStateAt("lift", 1.9));
        assertEquals("RAISING", run.mechanismStateAt("lift", 2.0));
        assertEquals(List.of("lift", "intake"), run.mechanismNames());
    }

    @Test
    void sumsPointsFromEvents() {
        Run run = blueLeft()
                .event(Event.of(3, "score.sample", Map.of("points", "4")))
                .event(Event.of(9, "score.sample", Map.of("points", "2")))
                .build();

        assertEquals(6.0, run.pointsScored());
        assertEquals(2, run.events("score.sample").size());
    }

    @Test
    void successRequiresCompletionWithoutErrors() {
        assertTrue(blueLeft().build().succeeded());
        assertFalse(blueLeft().status(RunStatus.INCOMPLETE).build().succeeded());
        assertFalse(blueLeft().log(LogEntry.of(1, LogLevel.ERROR, "lift stalled")).build().succeeded());
        assertTrue(blueLeft().log(LogEntry.of(1, LogLevel.WARNING, "slow")).build().succeeded());
    }

    @Test
    void withTestResultsReplacesResultsWithoutMutatingOriginal() {
        Run original = blueLeft().testResult(TestResult.passed("old")).build();

        Run updated = original.withTestResults(List.of(TestResult.failed("new", "too slow")));

        assertEquals("old", original.testResults().get(0).name());
        assertEquals(List.of(TestResult.failed("new", "too slow")), updated.testResults());
    }

    @Test
    void rejectsNegativeDurationAndMissingMetadata() {
        assertThrows(IllegalArgumentException.class, () -> blueLeft().duration(-1).build());
        assertThrows(IllegalArgumentException.class, () -> Run.builder("x", null).build());
    }

    @Test
    void phasesRejectInvertedIntervals() {
        assertThrows(IllegalArgumentException.class, () -> MatchPhase.of("teleop", 10, 5));
        assertEquals("teleop", blueLeft().phase(MatchPhase.of("teleop", 38, 158)).build().phase("teleop").name());
    }

    @Test
    void generatesSortableFileSafeIds() {
        String id = RunIds.generate("Blue Left!", 1_789_320_612_000L, new Random(1));

        assertTrue(id.matches("\\d{8}-\\d{6}-blue-left--[0-9a-f]{4}"), id);
    }
}
