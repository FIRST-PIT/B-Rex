package dev.brex.recording;

import dev.brex.core.run.Run;
import dev.brex.core.run.RunKind;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunSource;
import dev.brex.core.time.Clock;
import dev.brex.core.time.SystemClock;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Entry point for recording runs on the robot.
 *
 * <pre>{@code
 * RunRecorder brex = Brex.start("blue-left");
 * // in the loop
 * brex.trajectory.record(follower.getPose());
 * brex.telemetry.record("lift.position", lift.getCurrentPosition());
 * brex.event("deposit.complete");
 * // at the end of the routine
 * brex.complete();
 * }</pre>
 *
 * <p>B-rex keeps track of the most recently started recorder so that code without direct access
 * to it (a tele-op OpMode continuing a match, a mechanism library adapter) can find it with
 * {@link #current()}.
 */
public final class Brex {

    /** Default per-channel sample cap: about 30 minutes at 100 Hz. */
    public static final int DEFAULT_MAX_SAMPLES_PER_CHANNEL = 200_000;

    private static RunRecorder current;

    private Brex() {
    }

    /**
     * Starts recording an autonomous routine with default settings. On a robot controller (when
     * {@code /sdcard/FIRST} exists) the run is saved to {@link RunStore#ROBOT_DIRECTORY} when it
     * stops.
     */
    public static RunRecorder start(String name) {
        return withRobotDefaults(builder(name)).start();
    }

    /** Starts recording a full match; use {@link RunRecorder#phase(String)} to mark periods. */
    public static RunRecorder startMatch(String label) {
        return withRobotDefaults(builder(label).kind(RunKind.MATCH)).start();
    }

    private static Builder withRobotDefaults(Builder builder) {
        File first = RunStore.ROBOT_DIRECTORY.getParentFile().getParentFile();
        return first != null && first.isDirectory() ? builder.saveTo(RunStore.onRobot()) : builder;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** The most recently started recorder that has not stopped, or null. */
    public static synchronized RunRecorder current() {
        if (current != null && !current.isRecording()) {
            current = null;
        }
        return current;
    }

    private static synchronized void setCurrent(RunRecorder recorder) {
        current = recorder;
    }

    /** Configures a recording before it starts. */
    public static final class Builder {

        private final RunMetadata.Builder metadata;
        private Clock clock = SystemClock.INSTANCE;
        private Long startedAtEpochMillis;
        private Random random;
        private int maxSamplesPerChannel = DEFAULT_MAX_SAMPLES_PER_CHANNEL;
        private final List<RunRecorder.StopListener> stopListeners = new ArrayList<>();

        private Builder(String name) {
            this.metadata = RunMetadata.builder(name);
        }

        public Builder kind(RunKind kind) {
            metadata.kind(kind);
            return this;
        }

        public Builder source(RunSource source) {
            metadata.source(source);
            return this;
        }

        public Builder robot(String robot) {
            metadata.robot(robot);
            return this;
        }

        public Builder robotConfig(String key, Object value) {
            metadata.robotConfig(key, String.valueOf(value));
            return this;
        }

        public Builder robotConfig(Map<String, String> config) {
            metadata.robotConfig(config);
            return this;
        }

        public Builder softwareVersion(String version) {
            metadata.softwareVersion(version);
            return this;
        }

        /** The Git commit the robot code was built from, typically injected by Gradle at build time. */
        public Builder gitCommit(String commit) {
            metadata.gitCommit(commit);
            return this;
        }

        public Builder gitBranch(String branch) {
            metadata.gitBranch(branch);
            return this;
        }

        public Builder gitDirty(Boolean dirty) {
            metadata.gitDirty(dirty);
            return this;
        }

        public Builder tag(String tag) {
            metadata.tag(tag);
            return this;
        }

        public Builder property(String key, String value) {
            metadata.property(key, value);
            return this;
        }

        /** Time source; defaults to {@link SystemClock}. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** Wall-clock start time; defaults to {@link System#currentTimeMillis()}. */
        public Builder startedAtEpochMillis(long millis) {
            this.startedAtEpochMillis = millis;
            return this;
        }

        /** Randomness for the run ID suffix; set for reproducible IDs in tests and simulations. */
        public Builder random(Random random) {
            this.random = random;
            return this;
        }

        public Builder maxSamplesPerChannel(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException("maxSamplesPerChannel must be positive");
            }
            this.maxSamplesPerChannel = max;
            return this;
        }

        /** Called with the finished run when recording stops. May be called repeatedly to add listeners. */
        public Builder onStop(RunRecorder.StopListener listener) {
            this.stopListeners.add(listener);
            return this;
        }

        /** Saves the run to {@code store} when recording stops. Save failures never throw. */
        public Builder saveTo(final RunStore store) {
            return onStop(new RunRecorder.StopListener() {
                @Override
                public void onStop(Run run) {
                    store.saveQuietly(run);
                }
            });
        }

        public RunRecorder start() {
            long started = startedAtEpochMillis != null ? startedAtEpochMillis : System.currentTimeMillis();
            RunRecorder recorder = new RunRecorder(metadata, clock, started,
                    random != null ? random : new Random(), maxSamplesPerChannel);
            for (RunRecorder.StopListener listener : stopListeners) {
                recorder.addStopListener(listener);
            }
            setCurrent(recorder);
            return recorder;
        }
    }
}
