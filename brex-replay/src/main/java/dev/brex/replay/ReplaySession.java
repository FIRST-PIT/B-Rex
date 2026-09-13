package dev.brex.replay;

import dev.brex.core.event.Event;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A cursor over a recorded run. Seek to any time (or phase, or timeline entry) and inspect the
 * reconstructed robot state. This is the engine behind {@code brex replay} and the B-rex Lab
 * scrubber.
 */
public final class ReplaySession {

    private final Run run;
    private final Timeline timeline;
    private double time;

    public ReplaySession(Run run) {
        this.run = run;
        this.timeline = Timeline.of(run);
    }

    public Run run() {
        return run;
    }

    public Timeline timeline() {
        return timeline;
    }

    public double time() {
        return time;
    }

    public boolean atEnd() {
        return time >= run.duration();
    }

    /** Moves the cursor to {@code seconds}, clamped to the run. */
    public ReplaySession seek(double seconds) {
        time = Math.max(0, Math.min(run.duration(), seconds));
        return this;
    }

    /** Moves the cursor by {@code seconds} (negative steps backwards). */
    public ReplaySession step(double seconds) {
        return seek(time + seconds);
    }

    /** Moves to the start of a phase such as {@code teleop}. */
    public ReplaySession seekPhase(String name) {
        MatchPhase phase = run.phase(name);
        if (phase == null) {
            List<String> names = run.phases().stream().map(MatchPhase::name).toList();
            throw new IllegalArgumentException("Run " + run.id() + " has no phase '" + name + "'"
                    + (names.isEmpty() ? " (it has no phases)" : " (phases: " + String.join(", ", names) + ")"));
        }
        return seek(phase.start());
    }

    /**
     * Moves to the next timeline entry strictly after the cursor and returns it, or returns null
     * (and moves to the end) when there are no more entries.
     */
    public TimelineEntry next() {
        for (TimelineEntry entry : timeline.entries()) {
            if (entry.time() > time) {
                seek(entry.time());
                return entry;
            }
        }
        seek(run.duration());
        return null;
    }

    /** The robot state at the cursor. */
    public RobotSnapshot snapshot() {
        return snapshotAt(time);
    }

    /** The robot state at any time, without moving the cursor. */
    public RobotSnapshot snapshotAt(double t) {
        Pose pose = run.trajectory().isEmpty() ? null : run.trajectory().poseAt(t);
        Velocity velocity = run.trajectory().isEmpty() ? null : run.trajectory().velocityAt(t);

        Map<String, String> states = new LinkedHashMap<>();
        for (String mechanism : run.mechanismNames()) {
            String state = run.mechanismStateAt(mechanism, t);
            if (state != null) {
                states.put(mechanism, state);
            }
        }
        Map<String, Double> numbers = new LinkedHashMap<>();
        Map<String, String> texts = new LinkedHashMap<>();
        for (TelemetryChannel channel : run.telemetry().channels()) {
            int index = channel.indexAt(t);
            if (index < 0) {
                continue;
            }
            if (channel.type() == ChannelType.TEXT) {
                texts.put(channel.key(), channel.text(index));
            } else {
                numbers.put(channel.key(), channel.number(index));
            }
        }
        Event lastEvent = null;
        for (Event event : run.events()) {
            if (event.time() > t) {
                break;
            }
            lastEvent = event;
        }
        String phase = null;
        for (MatchPhase candidate : run.phases()) {
            if (candidate.contains(t)) {
                phase = candidate.name();
            }
        }
        return new RobotSnapshot(t, pose, velocity, states, numbers, texts, lastEvent, phase);
    }
}
