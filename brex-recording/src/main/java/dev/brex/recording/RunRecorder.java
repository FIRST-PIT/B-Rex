package dev.brex.recording;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunIds;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunStatus;
import dev.brex.core.run.TestResult;
import dev.brex.core.time.Clock;
import dev.brex.core.util.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Records one run in memory: telemetry, trajectory, events, mechanism states, log and phases.
 *
 * <p>Designed for robot loops:
 *
 * <ul>
 *   <li>Recording methods never throw. Invalid input becomes a warning inside the run, because a
 *       recording mistake must never stop an autonomous during a match.</li>
 *   <li>Samples are stored in primitive arrays to keep garbage collection low.</li>
 *   <li>All methods are thread-safe.</li>
 * </ul>
 *
 * <p>Obtain one through {@link Brex#start(String)} or {@link Brex#builder(String)}.
 */
public final class RunRecorder {

    /** Listener notified with the finished run when recording stops. */
    public interface StopListener {
        void onStop(Run run);
    }

    /** Telemetry for arbitrary keys. */
    public final TelemetryRecorder telemetry;

    /** Robot pose over time. */
    public final TrajectoryRecorder trajectory;

    private final String id;
    private final RunMetadata metadata;
    private final Clock clock;
    private final double startSeconds;
    private final int maxSamplesPerChannel;
    private final List<StopListener> stopListeners = new ArrayList<>();

    private final List<Event> events = new ArrayList<>();
    private final List<MechanismStateChange> mechanismStates = new ArrayList<>();
    private final Map<String, String> currentStates = new HashMap<>();
    private final List<LogEntry> log = new ArrayList<>();
    private final Set<String> internalWarnings = new HashSet<>();
    private final List<MatchPhase> phases = new ArrayList<>();
    private final List<TestResult> testResults = new ArrayList<>();

    private String openPhase;
    private double openPhaseStart;
    private Pose startPose;
    private RunStatus status;
    private double stopSeconds = Double.NaN;
    private Run finished;

    RunRecorder(RunMetadata.Builder metadata, Clock clock, long startedAtEpochMillis, Random random,
            int maxSamplesPerChannel) {
        this.clock = clock;
        this.startSeconds = clock.seconds();
        this.metadata = metadata.startedAtEpochMillis(startedAtEpochMillis).build();
        this.id = RunIds.generate(this.metadata.name(), startedAtEpochMillis, random);
        this.maxSamplesPerChannel = maxSamplesPerChannel;
        this.telemetry = new TelemetryRecorder(this);
        this.trajectory = new TrajectoryRecorder(this);
    }

    public String id() {
        return id;
    }

    public RunMetadata metadata() {
        return metadata;
    }

    /** Seconds since recording started (frozen once stopped). */
    public synchronized double elapsed() {
        return now();
    }

    public synchronized boolean isRecording() {
        return finished == null;
    }

    /** Records a named event such as {@code intake.start}. */
    public synchronized void event(String name) {
        event(name, null);
    }

    /** Records a named event with attributes. */
    public synchronized void event(String name, Map<String, String> attributes) {
        if (!acceptingSamples()) {
            return;
        }
        try {
            events.add(attributes == null ? Event.of(now(), name) : Event.of(now(), name, attributes));
        } catch (IllegalArgumentException e) {
            internalWarning(e.getMessage() + "; event dropped");
        }
    }

    /** Records an event that scored game points, for example {@code score("sample.high", 8)}. */
    public synchronized void score(String name, double points) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(Event.POINTS_ATTRIBUTE, formatPoints(points));
        event(name, attributes);
    }

    /**
     * Records that a mechanism is in a state. Calling this every loop is fine: only changes are
     * stored.
     */
    public synchronized void mechanism(String mechanism, String state) {
        if (!acceptingSamples()) {
            return;
        }
        if (state == null || state.equals(currentStates.get(mechanism))) {
            return;
        }
        try {
            mechanismStates.add(MechanismStateChange.of(now(), mechanism, state));
            currentStates.put(mechanism, state);
        } catch (IllegalArgumentException e) {
            internalWarning(e.getMessage() + "; state change dropped");
        }
    }

    /** Records an enum-based mechanism state using the constant's name. */
    public synchronized void mechanism(String mechanism, Enum<?> state) {
        mechanism(mechanism, state == null ? null : state.name());
    }

    public synchronized void info(String message) {
        addLog(LogLevel.INFO, message);
    }

    public synchronized void warn(String message) {
        addLog(LogLevel.WARNING, message);
    }

    public synchronized void error(String message) {
        addLog(LogLevel.ERROR, message);
    }

    /** Records an exception as an error and marks the run as failed. Recording continues. */
    public synchronized void fail(Throwable cause) {
        String message = cause == null ? "unknown failure" : cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        addLog(LogLevel.ERROR, message);
        if (finished == null) {
            status = RunStatus.FAILED;
        }
    }

    /** Records the intended starting pose, for example the pose given to the localizer. */
    public synchronized void startPose(Pose pose) {
        if (finished == null) {
            this.startPose = pose;
        }
    }

    /**
     * Ends the current phase (if any) and starts a new one, for example {@code phase("teleop")}.
     */
    public synchronized void phase(String name) {
        if (!acceptingSamples()) {
            return;
        }
        try {
            Names.requireValid("Phase name", name);
        } catch (IllegalArgumentException e) {
            internalWarning(e.getMessage() + "; phase ignored");
            return;
        }
        double t = now();
        closePhase(t);
        openPhase = name;
        openPhaseStart = t;
    }

    /** Ends the current phase without starting another. */
    public synchronized void endPhase() {
        closePhase(now());
    }

    /** Attaches the result of a check performed during the run, such as a hardware test. */
    public synchronized void testResult(TestResult result) {
        if (finished == null && result != null) {
            testResults.add(result);
        }
    }

    /**
     * Marks the routine as finished and stops recording. Call this at the very end of your
     * autonomous; a run that is stopped without it is recorded as {@link RunStatus#INCOMPLETE}.
     */
    public synchronized Run complete() {
        if (finished == null && status == null) {
            status = RunStatus.COMPLETED;
        }
        return stop();
    }

    /**
     * Stops recording and returns the finished run. Calling it again returns the same run. Stop
     * listeners (for example, the run store) are notified once.
     */
    public Run stop() {
        Run run;
        synchronized (this) {
            if (finished != null) {
                return finished;
            }
            stopSeconds = clock.seconds();
            closePhase(now());
            if (status == null) {
                status = RunStatus.INCOMPLETE;
            }
            finished = buildRun(status);
            run = finished;
        }
        for (StopListener listener : stopListeners) {
            listener.onStop(run);
        }
        return run;
    }

    /** Returns the run recorded so far without stopping. */
    public synchronized Run snapshot() {
        if (finished != null) {
            return finished;
        }
        return buildRun(status == null ? RunStatus.INCOMPLETE : status);
    }

    synchronized void addStopListener(StopListener listener) {
        stopListeners.add(listener);
    }

    double now() {
        double end = Double.isNaN(stopSeconds) ? clock.seconds() : stopSeconds;
        return Math.max(0, end - startSeconds);
    }

    boolean acceptingSamples() {
        return finished == null;
    }

    int maxSamplesPerChannel() {
        return maxSamplesPerChannel;
    }

    /** Adds a B-rex warning once per distinct message. */
    void internalWarning(String message) {
        if (internalWarnings.add(message)) {
            log.add(LogEntry.of(now(), LogLevel.WARNING, "B-rex: " + message));
        }
    }

    private void addLog(LogLevel level, String message) {
        if (acceptingSamples()) {
            log.add(LogEntry.of(now(), level, message));
        }
    }

    private void closePhase(double t) {
        if (openPhase != null) {
            phases.add(MatchPhase.of(openPhase, openPhaseStart, t));
            openPhase = null;
        }
    }

    private Run buildRun(RunStatus runStatus) {
        List<MatchPhase> allPhases = new ArrayList<>(phases);
        if (openPhase != null) {
            allPhases.add(MatchPhase.of(openPhase, openPhaseStart, now()));
        }
        return Run.builder(id, metadata)
                .status(runStatus)
                .duration(now())
                .startPose(startPose)
                .trajectory(trajectory.build())
                .telemetry(telemetry.build())
                .events(events)
                .mechanismStates(mechanismStates)
                .log(log)
                .phases(allPhases)
                .testResults(testResults)
                .build();
    }

    private static String formatPoints(double points) {
        return points == Math.rint(points) ? Long.toString((long) points) : Double.toString(points);
    }
}
