package dev.brex.analysis;

import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Time spent in each mechanism state occurrence. */
public final class MechanismDurations {

    private MechanismDurations() {
    }

    /**
     * Durations of every state occurrence, keyed by {@code mechanism:STATE}, in chronological
     * order. The last state of each mechanism is measured until the end of the run.
     */
    public static Map<String, List<Double>> of(Run run) {
        return collect(run, state -> true, true);
    }

    /**
     * Durations of state occurrences that ended with a later state change, keyed by
     * {@code mechanism:STATE}. The final state of each mechanism is excluded because its length
     * depends on when recording stopped, not on the mechanism. Use this when comparing runs.
     */
    public static Map<String, List<Double>> completed(Run run, Predicate<String> includeState) {
        return collect(run, includeState, false);
    }

    private static Map<String, List<Double>> collect(Run run, Predicate<String> includeState, boolean includeFinal) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        for (String mechanism : run.mechanismNames()) {
            List<MechanismStateChange> changes = run.mechanismStates(mechanism);
            for (int i = 0; i < changes.size(); i++) {
                MechanismStateChange change = changes.get(i);
                boolean last = i + 1 == changes.size();
                if ((last && !includeFinal) || !includeState.test(change.state())) {
                    continue;
                }
                double end = last ? run.duration() : changes.get(i + 1).time();
                result.computeIfAbsent(mechanism + ":" + change.state(), k -> new ArrayList<>())
                        .add(Math.max(0, end - change.time()));
            }
        }
        return result;
    }
}
