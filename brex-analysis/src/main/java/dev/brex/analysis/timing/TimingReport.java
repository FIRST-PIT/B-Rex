package dev.brex.analysis.timing;

import java.util.List;
import java.util.Optional;

/**
 * Raw timing measurements of one run. Nothing here is a recommendation: an idle period can be a
 * deliberate wait for an alliance partner, so teams decide what to optimize.
 *
 * @param duration run duration (s)
 * @param lanes activity per lane: {@code drive} first, then one lane per mechanism
 * @param idlePeriods gaps where no lane was active, at least {@code minIdleDuration} long
 * @param overlaps time two lanes were active simultaneously, per pair, largest first
 * @param longestStates the longest active mechanism state occurrences, longest first
 * @param cycle the most repeated sequence of mechanism states, if any repeats
 */
public record TimingReport(
        double duration,
        List<Lane> lanes,
        List<IdlePeriod> idlePeriods,
        List<Overlap> overlaps,
        List<StateTiming> longestStates,
        Optional<Cycle> cycle) {

    public TimingReport {
        lanes = List.copyOf(lanes);
        idlePeriods = List.copyOf(idlePeriods);
        overlaps = List.copyOf(overlaps);
        longestStates = List.copyOf(longestStates);
    }

    /** Total length of all idle periods (s). */
    public double totalIdle() {
        return idlePeriods.stream().mapToDouble(IdlePeriod::duration).sum();
    }

    /** An interval of activity. {@code label} is the mechanism state, or {@code moving} for drive. */
    public record Interval(double start, double end, String label) {
        public double duration() {
            return end - start;
        }
    }

    /** A named row of the timeline chart. */
    public record Lane(String name, List<Interval> intervals) {
        public Lane {
            intervals = List.copyOf(intervals);
        }

        public double activeTime() {
            return intervals.stream().mapToDouble(Interval::duration).sum();
        }

        /** Fraction of {@code [from, to]} covered by activity. */
        public double coverage(double from, double to) {
            double covered = 0;
            for (Interval interval : intervals) {
                covered += Math.max(0, Math.min(to, interval.end()) - Math.max(from, interval.start()));
            }
            return to > from ? covered / (to - from) : 0;
        }
    }

    /**
     * A period with no activity.
     *
     * @param before the lane activity that ended at {@code start}, or null at the start of the run
     * @param after the lane activity that started at {@code end}, or null at the end of the run
     * @param lastEvent the most recent event at or before {@code start}, or null
     */
    public record IdlePeriod(double start, double end, String before, String after, String lastEvent) {
        public double duration() {
            return end - start;
        }
    }

    /** Two lanes working at the same time. */
    public record Overlap(String laneA, String laneB, double seconds) {
    }

    /**
     * One mechanism state occurrence.
     *
     * @param occurrence zero-based index among occurrences of this state
     * @param baselineDuration the same occurrence in the baseline, or NaN
     */
    public record StateTiming(String mechanism, String state, int occurrence, double start, double duration,
            double baselineDuration) {
    }

    /**
     * A repeated sequence of mechanism states, such as intake → lift → deposit.
     *
     * @param steps the repeated {@code mechanism:STATE} sequence
     * @param starts start time of each repetition
     * @param durations time from the start of each repetition to the start of the next, or to the
     *     end of the last step for the final repetition
     */
    public record Cycle(List<String> steps, List<Double> starts, List<Double> durations) {
        public Cycle {
            steps = List.copyOf(steps);
            starts = List.copyOf(starts);
            durations = List.copyOf(durations);
        }

        public int repetitions() {
            return starts.size();
        }
    }
}
