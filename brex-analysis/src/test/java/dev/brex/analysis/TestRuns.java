package dev.brex.analysis;

import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.trajectory.Trajectory;
import java.util.function.DoubleFunction;

/** Builders for synthetic runs used across analysis tests. */
public final class TestRuns {

    private TestRuns() {
    }

    /** A trajectory sampled at 50 Hz from {@code 0} to {@code duration} using {@code path(t)}. */
    public static Trajectory sampled(double duration, DoubleFunction<Pose> path) {
        Trajectory.Builder builder = new Trajectory.Builder();
        int steps = (int) Math.round(duration * 50);
        for (int i = 0; i <= steps; i++) {
            double t = i / 50.0;
            builder.add(t, path.apply(t));
        }
        return builder.build();
    }

    /** Drives 2 m along +x at a constant speed over {@code duration} seconds, offset in y. */
    public static Trajectory straight(double duration, double yOffset) {
        return sampled(duration, t -> Pose.of(2.0 * t / duration, yOffset, 0));
    }

    /** A typical routine: drive, intake, lift, deposit. {@code slowdown} stretches all timing. */
    public static Run.Builder cycle(String id, double slowdown, double yOffset) {
        double s = slowdown;
        return Run.builder(id, RunMetadata.builder("blue-left").build())
                .duration(10 * s)
                .trajectory(straight(10 * s, yOffset))
                .mechanismState(MechanismStateChange.of(0, "intake", "IDLE"))
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .event(Event.of(2 * s, "alignment.complete"))
                .event(Event.of(3 * s, "intake.start"))
                .mechanismState(MechanismStateChange.of(3 * s, "intake", "RUNNING"))
                .mechanismState(MechanismStateChange.of(4 * s, "intake", "IDLE"))
                .mechanismState(MechanismStateChange.of(5 * s, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(6 * s, "lift", "IDLE"))
                .event(Event.of(7 * s, "deposit.start"))
                .event(Event.of(8 * s, "deposit.complete"));
    }
}
