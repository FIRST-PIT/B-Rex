package dev.brex.replay;

import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays two runs of the same routine on a shared clock, so the current run can be watched
 * against a previous run (the ghost).
 */
public final class GhostReplay {

    /**
     * Both robots at the same moment.
     *
     * @param time seconds since start
     * @param current state of the run being examined
     * @param ghost state of the reference run at the same time
     * @param positionError distance between the two poses in meters, NaN if either has no pose
     * @param headingError signed heading difference (current minus ghost) in radians, or NaN
     */
    public record Frame(double time, RobotSnapshot current, RobotSnapshot ghost, double positionError,
            double headingError) {
    }

    private final ReplaySession current;
    private final ReplaySession ghost;

    public GhostReplay(Run current, Run ghost) {
        this.current = new ReplaySession(current);
        this.ghost = new ReplaySession(ghost);
    }

    /** The longer of the two durations. */
    public double duration() {
        return Math.max(current.run().duration(), ghost.run().duration());
    }

    /** Both runs at time {@code t}. A run that already ended holds its final state. */
    public Frame frameAt(double t) {
        RobotSnapshot a = current.snapshotAt(Math.min(t, current.run().duration()));
        RobotSnapshot b = ghost.snapshotAt(Math.min(t, ghost.run().duration()));
        boolean poses = a.pose() != null && b.pose() != null;
        return new Frame(t, a, b, poses ? a.pose().distanceTo(b.pose()) : Double.NaN,
                poses ? b.pose().headingDifferenceTo(a.pose()) : Double.NaN);
    }

    /** Frames every {@code step} seconds from 0 through the longer duration, inclusive. */
    public List<Frame> frames(double step) {
        if (!(step > 0)) {
            throw new IllegalArgumentException("Replay step must be positive (was " + step + ")");
        }
        List<Frame> frames = new ArrayList<>();
        int count = (int) Math.floor(duration() / step + 1e-9);
        for (int i = 0; i <= count; i++) {
            frames.add(frameAt(i * step));
        }
        if (frames.get(frames.size() - 1).time() < duration()) {
            frames.add(frameAt(duration()));
        }
        return frames;
    }
}
