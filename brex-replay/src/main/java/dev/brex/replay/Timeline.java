package dev.brex.replay;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every discrete occurrence of a run (phases, events, mechanism state changes and log entries)
 * merged into one chronological list. Continuous data (pose, telemetry) is reconstructed on demand
 * by {@link ReplaySession}.
 */
public final class Timeline {

    private final List<TimelineEntry> entries;
    private final double duration;

    private Timeline(List<TimelineEntry> entries, double duration) {
        this.entries = List.copyOf(entries);
        this.duration = duration;
    }

    public static Timeline of(Run run) {
        List<TimelineEntry> entries = new ArrayList<>();
        for (MatchPhase phase : run.phases()) {
            entries.add(new TimelineEntry(phase.start(), TimelineEntry.Kind.PHASE_START, phase.name(), "", null));
            entries.add(new TimelineEntry(phase.end(), TimelineEntry.Kind.PHASE_END, phase.name(), "", null));
        }
        for (Event event : run.events()) {
            String detail = event.attributes().isEmpty() ? "" : event.attributes().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" "));
            entries.add(new TimelineEntry(event.time(), TimelineEntry.Kind.EVENT, event.name(), detail, null));
        }
        Map<String, String> lastState = new HashMap<>();
        for (MechanismStateChange change : run.mechanismStates()) {
            String previous = lastState.put(change.mechanism(), change.state());
            entries.add(new TimelineEntry(change.time(), TimelineEntry.Kind.MECHANISM_STATE, change.mechanism(),
                    change.state(), previous));
        }
        for (LogEntry log : run.log()) {
            entries.add(new TimelineEntry(log.time(), TimelineEntry.Kind.LOG, log.level().name(), log.message(),
                    null));
        }
        // Stable sort; at equal times phase starts come first and phase ends last.
        entries.sort(Comparator.comparingDouble(TimelineEntry::time).thenComparingInt(Timeline::tieOrder));
        return new Timeline(entries, run.duration());
    }

    private static int tieOrder(TimelineEntry entry) {
        return switch (entry.kind()) {
            case PHASE_START -> 0;
            case PHASE_END -> 2;
            default -> 1;
        };
    }

    public List<TimelineEntry> entries() {
        return entries;
    }

    public double duration() {
        return duration;
    }

    public int size() {
        return entries.size();
    }

    /** Entries with {@code from <= time <= to}. */
    public List<TimelineEntry> between(double from, double to) {
        return entries.stream().filter(e -> e.time() >= from && e.time() <= to).toList();
    }

    /** A timeline containing only the given kinds. */
    public Timeline only(Set<TimelineEntry.Kind> kinds) {
        EnumSet<TimelineEntry.Kind> allowed = EnumSet.copyOf(kinds);
        return new Timeline(entries.stream().filter(e -> allowed.contains(e.kind())).toList(), duration);
    }
}
