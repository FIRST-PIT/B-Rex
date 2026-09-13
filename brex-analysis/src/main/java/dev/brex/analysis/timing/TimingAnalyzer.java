package dev.brex.analysis.timing;

import dev.brex.analysis.MechanismDurations;
import dev.brex.analysis.timing.TimingReport.Cycle;
import dev.brex.analysis.timing.TimingReport.IdlePeriod;
import dev.brex.analysis.timing.TimingReport.Interval;
import dev.brex.analysis.timing.TimingReport.Lane;
import dev.brex.analysis.timing.TimingReport.Overlap;
import dev.brex.analysis.timing.TimingReport.StateTiming;
import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.run.Run;
import dev.brex.core.trajectory.Trajectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds where an autonomous spends its time.
 *
 * <ul>
 *   <li><b>Drive lane</b>: the robot is moving when its speed exceeds {@code idleSpeed} or its
 *       turn rate exceeds {@code idleTurnRate}.</li>
 *   <li><b>Mechanism lanes</b>: a mechanism is active while it is in a state that is not idle.</li>
 *   <li><b>Idle periods</b>: no lane is active for at least {@code minIdleDuration}.</li>
 *   <li><b>Overlaps</b>: time two lanes are active at once. This is usually good (parallel work),
 *       so it is reported for context, not as a problem.</li>
 *   <li><b>Cycles</b>: the most frequently repeated contiguous sequence of active states.</li>
 * </ul>
 */
public final class TimingAnalyzer {

    public static final String DRIVE_LANE = "drive";

    private final TimingOptions options;

    public TimingAnalyzer(TimingOptions options) {
        this.options = options;
    }

    public TimingReport analyze(Run run) {
        return analyze(run, null);
    }

    /** Analyzes {@code run}; with a baseline, state timings include the baseline's durations. */
    public TimingReport analyze(Run run, Run baseline) {
        List<Lane> lanes = new ArrayList<>();
        lanes.add(new Lane(DRIVE_LANE, driveIntervals(run)));
        for (String mechanism : run.mechanismNames()) {
            lanes.add(new Lane(mechanism, mechanismIntervals(run, mechanism)));
        }
        return new TimingReport(run.duration(), lanes, idlePeriods(run, lanes), overlaps(lanes),
                longestStates(run, baseline), cycle(run));
    }

    private List<Interval> driveIntervals(Run run) {
        Trajectory trajectory = run.trajectory();
        List<Interval> intervals = new ArrayList<>();
        double start = Double.NaN;
        for (int i = 0; i < trajectory.size(); i++) {
            Velocity v = trajectory.velocity(i);
            boolean moving = v.speed() > options.idleSpeed() || Math.abs(v.omega()) > options.idleTurnRate();
            double t = trajectory.time(i);
            if (moving && Double.isNaN(start)) {
                start = t;
            } else if (!moving && !Double.isNaN(start)) {
                intervals.add(new Interval(start, t, "moving"));
                start = Double.NaN;
            }
        }
        if (!Double.isNaN(start)) {
            intervals.add(new Interval(start, trajectory.endTime(), "moving"));
        }
        return merge(intervals);
    }

    private List<Interval> mechanismIntervals(Run run, String mechanism) {
        List<MechanismStateChange> changes = run.mechanismStates(mechanism);
        List<Interval> intervals = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            MechanismStateChange change = changes.get(i);
            if (options.idleStates().isIdle(change.state())) {
                continue;
            }
            double end = i + 1 < changes.size() ? changes.get(i + 1).time() : run.duration();
            if (end > change.time()) {
                intervals.add(new Interval(change.time(), end, change.state()));
            }
        }
        return intervals;
    }

    /** Joins intervals separated by less than {@code mergeGap}. */
    private List<Interval> merge(List<Interval> intervals) {
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : intervals) {
            if (!merged.isEmpty() && interval.start() - merged.get(merged.size() - 1).end() < options.mergeGap()) {
                Interval last = merged.remove(merged.size() - 1);
                merged.add(new Interval(last.start(), interval.end(), last.label()));
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    private List<IdlePeriod> idlePeriods(Run run, List<Lane> lanes) {
        record Tagged(Interval interval, String lane) {
        }
        List<Tagged> all = new ArrayList<>();
        for (Lane lane : lanes) {
            for (Interval interval : lane.intervals()) {
                all.add(new Tagged(interval, lane.name()));
            }
        }
        all.sort(Comparator.comparingDouble(t -> t.interval().start()));

        List<IdlePeriod> periods = new ArrayList<>();
        double cursor = 0;
        String before = null;
        for (Tagged tagged : all) {
            Interval interval = tagged.interval();
            if (interval.start() - cursor >= options.minIdleDuration()) {
                periods.add(new IdlePeriod(cursor, interval.start(), before, describe(tagged.lane(), interval),
                        lastEventAt(run, cursor)));
            }
            if (interval.end() > cursor) {
                cursor = interval.end();
                before = describe(tagged.lane(), interval);
            }
        }
        if (run.duration() - cursor >= options.minIdleDuration()) {
            periods.add(new IdlePeriod(cursor, run.duration(), before, null, lastEventAt(run, cursor)));
        }
        return periods;
    }

    private static String describe(String lane, Interval interval) {
        return lane.equals(DRIVE_LANE) ? DRIVE_LANE : lane + " " + interval.label();
    }

    private static String lastEventAt(Run run, double time) {
        String name = null;
        for (Event event : run.events()) {
            if (event.time() > time + 1e-9) {
                break;
            }
            name = event.name();
        }
        return name;
    }

    private static List<Overlap> overlaps(List<Lane> lanes) {
        List<Overlap> overlaps = new ArrayList<>();
        for (int a = 0; a < lanes.size(); a++) {
            for (int b = a + 1; b < lanes.size(); b++) {
                double seconds = 0;
                for (Interval x : lanes.get(a).intervals()) {
                    for (Interval y : lanes.get(b).intervals()) {
                        seconds += Math.max(0, Math.min(x.end(), y.end()) - Math.max(x.start(), y.start()));
                    }
                }
                if (seconds > 0) {
                    overlaps.add(new Overlap(lanes.get(a).name(), lanes.get(b).name(), seconds));
                }
            }
        }
        overlaps.sort(Comparator.comparingDouble(Overlap::seconds).reversed());
        return overlaps;
    }

    private List<StateTiming> longestStates(Run run, Run baseline) {
        Map<String, List<Double>> baselineDurations = baseline == null ? Map.of()
                : MechanismDurations.completed(baseline, s -> !options.idleStates().isIdle(s));
        Map<String, Integer> occurrences = new HashMap<>();
        List<StateTiming> timings = new ArrayList<>();
        for (String mechanism : run.mechanismNames()) {
            List<MechanismStateChange> changes = run.mechanismStates(mechanism);
            for (int i = 0; i + 1 < changes.size(); i++) {
                MechanismStateChange change = changes.get(i);
                if (options.idleStates().isIdle(change.state())) {
                    continue;
                }
                String key = mechanism + ":" + change.state();
                int occurrence = occurrences.merge(key, 1, Integer::sum) - 1;
                List<Double> base = baselineDurations.getOrDefault(key, List.of());
                timings.add(new StateTiming(mechanism, change.state(), occurrence, change.time(),
                        changes.get(i + 1).time() - change.time(),
                        occurrence < base.size() ? base.get(occurrence) : Double.NaN));
            }
        }
        timings.sort(Comparator.comparingDouble(StateTiming::duration).reversed());
        return timings.subList(0, Math.min(10, timings.size()));
    }

    /**
     * The contiguous sequence of 2 to 6 active state entries that repeats (without overlapping) the
     * most times; ties prefer longer sequences.
     */
    private Optional<Cycle> cycle(Run run) {
        List<MechanismStateChange> active = run.mechanismStates().stream()
                .filter(c -> !options.idleStates().isIdle(c.state())).toList();
        List<String> tokens = active.stream().map(c -> c.mechanism() + ":" + c.state()).toList();
        List<String> bestSteps = null;
        List<Integer> bestStarts = List.of();
        for (int length = 2; length <= Math.min(6, tokens.size() / 2); length++) {
            for (int from = 0; from + length <= tokens.size(); from++) {
                List<String> candidate = tokens.subList(from, from + length);
                List<Integer> starts = new ArrayList<>();
                for (int i = 0; i + length <= tokens.size(); ) {
                    if (tokens.subList(i, i + length).equals(candidate)) {
                        starts.add(i);
                        i += length;
                    } else {
                        i++;
                    }
                }
                if (starts.size() >= 2 && (starts.size() > bestStarts.size()
                        || (starts.size() == bestStarts.size() && length > bestSteps.size()))) {
                    bestSteps = List.copyOf(candidate);
                    bestStarts = starts;
                }
            }
        }
        if (bestSteps == null) {
            return Optional.empty();
        }
        List<Double> startTimes = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        for (int k = 0; k < bestStarts.size(); k++) {
            int first = bestStarts.get(k);
            double start = active.get(first).time();
            startTimes.add(start);
            double end;
            if (k + 1 < bestStarts.size()) {
                end = active.get(bestStarts.get(k + 1)).time();
            } else {
                int last = first + bestSteps.size() - 1;
                end = last + 1 < active.size() ? active.get(last + 1).time() : run.duration();
            }
            durations.add(end - start);
        }
        return Optional.of(new Cycle(bestSteps, startTimes, durations));
    }
}
