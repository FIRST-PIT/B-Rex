package dev.brex.core.run;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import dev.brex.core.util.Names;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One recorded execution of an autonomous routine, tele-op period, match or hardware test.
 *
 * <p>A run is immutable. All times inside it are seconds since the run started. The run model is
 * independent of any file format or UI; see {@code dev.brex.core.format.RunFormat} for
 * persistence.
 */
public final class Run {

    private static final Comparator<Event> EVENT_ORDER = new Comparator<Event>() {
        @Override
        public int compare(Event a, Event b) {
            return Double.compare(a.time(), b.time());
        }
    };
    private static final Comparator<MechanismStateChange> STATE_ORDER = new Comparator<MechanismStateChange>() {
        @Override
        public int compare(MechanismStateChange a, MechanismStateChange b) {
            return Double.compare(a.time(), b.time());
        }
    };
    private static final Comparator<LogEntry> LOG_ORDER = new Comparator<LogEntry>() {
        @Override
        public int compare(LogEntry a, LogEntry b) {
            return Double.compare(a.time(), b.time());
        }
    };
    private static final Comparator<MatchPhase> PHASE_ORDER = new Comparator<MatchPhase>() {
        @Override
        public int compare(MatchPhase a, MatchPhase b) {
            return Double.compare(a.start(), b.start());
        }
    };

    private final String id;
    private final RunMetadata metadata;
    private final RunStatus status;
    private final double duration;
    private final Pose startPose;
    private final Pose endPose;
    private final Trajectory trajectory;
    private final Telemetry telemetry;
    private final List<Event> events;
    private final List<MechanismStateChange> mechanismStates;
    private final List<LogEntry> log;
    private final List<MatchPhase> phases;
    private final List<TestResult> testResults;

    private Run(Builder b) {
        this.id = Names.requireValid("Run id", b.id);
        if (b.metadata == null) {
            throw new IllegalArgumentException("Run '" + b.id + "' has no metadata");
        }
        this.metadata = b.metadata;
        this.status = b.status;
        this.trajectory = b.trajectory;
        this.telemetry = b.telemetry;
        this.events = sortedCopy(b.events, EVENT_ORDER);
        this.mechanismStates = sortedCopy(b.mechanismStates, STATE_ORDER);
        this.log = sortedCopy(b.log, LOG_ORDER);
        this.phases = sortedCopy(b.phases, PHASE_ORDER);
        this.testResults = Collections.unmodifiableList(new ArrayList<>(b.testResults));
        this.duration = Double.isNaN(b.duration) ? inferDuration() : b.duration;
        if (duration < 0) {
            throw new IllegalArgumentException("Run '" + b.id + "' has negative duration " + duration);
        }
        this.startPose = b.startPose;
        this.endPose = b.endPose;
    }

    public static Builder builder(String id, RunMetadata metadata) {
        return new Builder(id, metadata);
    }

    public String id() {
        return id;
    }

    public RunMetadata metadata() {
        return metadata;
    }

    /** Shorthand for {@code metadata().name()}. */
    public String name() {
        return metadata.name();
    }

    public RunStatus status() {
        return status;
    }

    /** Seconds from start of recording until completion or stop. */
    public double duration() {
        return duration;
    }

    /** The explicitly recorded start pose, else the first trajectory pose, else null. */
    public Pose startPose() {
        if (startPose != null) {
            return startPose;
        }
        return trajectory.isEmpty() ? null : trajectory.startPose();
    }

    /** The explicitly recorded end pose, else the last trajectory pose, else null. */
    public Pose endPose() {
        if (endPose != null) {
            return endPose;
        }
        return trajectory.isEmpty() ? null : trajectory.endPose();
    }

    public Trajectory trajectory() {
        return trajectory;
    }

    public Telemetry telemetry() {
        return telemetry;
    }

    /** All events in chronological order. */
    public List<Event> events() {
        return events;
    }

    /** Events with the given name in chronological order. */
    public List<Event> events(String name) {
        List<Event> result = new ArrayList<>();
        for (Event event : events) {
            if (event.name().equals(name)) {
                result.add(event);
            }
        }
        return result;
    }

    /** The first event with this name, or null. */
    public Event firstEvent(String name) {
        for (Event event : events) {
            if (event.name().equals(name)) {
                return event;
            }
        }
        return null;
    }

    public boolean hasEvent(String name) {
        return firstEvent(name) != null;
    }

    /** Distinct event names in order of first occurrence. */
    public List<String> eventNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Event event : events) {
            names.add(event.name());
        }
        return new ArrayList<>(names);
    }

    /** Sum of the {@code points} attribute over all events. */
    public double pointsScored() {
        double total = 0;
        for (Event event : events) {
            total += event.points();
        }
        return total;
    }

    /** All mechanism state changes in chronological order. */
    public List<MechanismStateChange> mechanismStates() {
        return mechanismStates;
    }

    /** State changes of one mechanism in chronological order. */
    public List<MechanismStateChange> mechanismStates(String mechanism) {
        List<MechanismStateChange> result = new ArrayList<>();
        for (MechanismStateChange change : mechanismStates) {
            if (change.mechanism().equals(mechanism)) {
                result.add(change);
            }
        }
        return result;
    }

    /** Distinct mechanism names in order of first state change. */
    public List<String> mechanismNames() {
        Set<String> names = new LinkedHashSet<>();
        for (MechanismStateChange change : mechanismStates) {
            names.add(change.mechanism());
        }
        return new ArrayList<>(names);
    }

    /** The state a mechanism was in at {@code time}, or null before its first recorded state. */
    public String mechanismStateAt(String mechanism, double time) {
        String state = null;
        for (MechanismStateChange change : mechanismStates) {
            if (change.time() > time) {
                break;
            }
            if (change.mechanism().equals(mechanism)) {
                state = change.state();
            }
        }
        return state;
    }

    public List<LogEntry> log() {
        return log;
    }

    public List<LogEntry> warnings() {
        return logAt(LogLevel.WARNING);
    }

    public List<LogEntry> errors() {
        return logAt(LogLevel.ERROR);
    }

    public List<MatchPhase> phases() {
        return phases;
    }

    /** The phase with this name, or null. */
    public MatchPhase phase(String name) {
        for (MatchPhase phase : phases) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        return null;
    }

    public List<TestResult> testResults() {
        return testResults;
    }

    /** A run succeeded when it completed and recorded no errors. */
    public boolean succeeded() {
        return status == RunStatus.COMPLETED && errors().isEmpty();
    }

    /** A copy of this run with different metadata, for example after stamping a Git commit. */
    public Run withMetadata(RunMetadata newMetadata) {
        return toBuilder().metadata(newMetadata).build();
    }

    /** A copy of this run with the given test results replacing the existing ones. */
    public Run withTestResults(Collection<TestResult> results) {
        Builder b = toBuilder();
        b.testResults.clear();
        b.testResults.addAll(results);
        return b.build();
    }

    public Builder toBuilder() {
        Builder b = new Builder(id, metadata)
                .status(status)
                .duration(duration)
                .startPose(startPose)
                .endPose(endPose)
                .trajectory(trajectory)
                .telemetry(telemetry);
        b.events.addAll(events);
        b.mechanismStates.addAll(mechanismStates);
        b.log.addAll(log);
        b.phases.addAll(phases);
        b.testResults.addAll(testResults);
        return b;
    }

    private List<LogEntry> logAt(LogLevel level) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : log) {
            if (entry.level() == level) {
                result.add(entry);
            }
        }
        return result;
    }

    private double inferDuration() {
        double end = 0;
        if (!trajectory.isEmpty()) {
            end = Math.max(end, trajectory.endTime());
        }
        for (Event event : events) {
            end = Math.max(end, event.time());
        }
        for (MechanismStateChange change : mechanismStates) {
            end = Math.max(end, change.time());
        }
        for (TelemetryChannel channel : telemetry.channels()) {
            if (!channel.isEmpty()) {
                end = Math.max(end, channel.time(channel.size() - 1));
            }
        }
        return end;
    }

    private static <T> List<T> sortedCopy(List<T> items, Comparator<T> order) {
        List<T> copy = new ArrayList<>(items);
        Collections.sort(copy, order); // stable: equal timestamps keep recording order
        return Collections.unmodifiableList(copy);
    }

    @Override
    public String toString() {
        return "Run(" + id + ", " + metadata.name() + ", " + status + ", " + duration + "s)";
    }

    /** Builder for {@link Run}. */
    public static final class Builder {
        private final String id;
        private RunMetadata metadata;
        private RunStatus status = RunStatus.COMPLETED;
        private double duration = Double.NaN;
        private Pose startPose;
        private Pose endPose;
        private Trajectory trajectory = Trajectory.empty();
        private Telemetry telemetry = Telemetry.empty();
        private final List<Event> events = new ArrayList<>();
        private final List<MechanismStateChange> mechanismStates = new ArrayList<>();
        private final List<LogEntry> log = new ArrayList<>();
        private final List<MatchPhase> phases = new ArrayList<>();
        private final List<TestResult> testResults = new ArrayList<>();

        private Builder(String id, RunMetadata metadata) {
            this.id = id;
            this.metadata = metadata;
        }

        public Builder metadata(RunMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder status(RunStatus status) {
            this.status = status == null ? RunStatus.COMPLETED : status;
            return this;
        }

        /** Sets the duration. When not set, it is inferred from the latest recorded timestamp. */
        public Builder duration(double seconds) {
            this.duration = seconds;
            return this;
        }

        public Builder startPose(Pose pose) {
            this.startPose = pose;
            return this;
        }

        public Builder endPose(Pose pose) {
            this.endPose = pose;
            return this;
        }

        public Builder trajectory(Trajectory trajectory) {
            this.trajectory = trajectory == null ? Trajectory.empty() : trajectory;
            return this;
        }

        public Builder telemetry(Telemetry telemetry) {
            this.telemetry = telemetry == null ? Telemetry.empty() : telemetry;
            return this;
        }

        public Builder event(Event event) {
            this.events.add(event);
            return this;
        }

        public Builder events(Collection<Event> events) {
            this.events.addAll(events);
            return this;
        }

        public Builder mechanismState(MechanismStateChange change) {
            this.mechanismStates.add(change);
            return this;
        }

        public Builder mechanismStates(Collection<MechanismStateChange> changes) {
            this.mechanismStates.addAll(changes);
            return this;
        }

        public Builder log(LogEntry entry) {
            this.log.add(entry);
            return this;
        }

        public Builder log(Collection<LogEntry> entries) {
            this.log.addAll(entries);
            return this;
        }

        public Builder phase(MatchPhase phase) {
            this.phases.add(phase);
            return this;
        }

        public Builder phases(Collection<MatchPhase> phases) {
            this.phases.addAll(phases);
            return this;
        }

        public Builder testResult(TestResult result) {
            this.testResults.add(result);
            return this;
        }

        public Builder testResults(Collection<TestResult> results) {
            this.testResults.addAll(results);
            return this;
        }

        public Run build() {
            return new Run(this);
        }
    }
}
