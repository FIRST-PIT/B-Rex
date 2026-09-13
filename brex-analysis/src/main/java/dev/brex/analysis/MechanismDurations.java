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
     * Durations keyed by {@code mechanism:STATE}, one entry per occurrence in chronological order.
     * The last state of each mechanism lasts until the end of the run.
     */
    public static Map<String, List<Double>> of(Run run) {
        return of(run, state -> true);
    }

    /** Like {@link #of(Run)}, keeping only states accepted by {@code includeState}. */
    public static Map<String, List<Double>> of(Run run, Predicate<String> includeState) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        for (String mechanism : run.mechanismNames()) {
            List<MechanismStateChange> changes = run.mechanismStates(mechanism);
            for (int i = 0; i < changes.size(); i++) {
                MechanismStateChange change = changes.get(i);
                if (!includeState.test(change.state())) {
                    continue;
                }
                double end = i + 1 < changes.size() ? changes.get(i + 1).time() : run.duration();
                result.computeIfAbsent(mechanism + ":" + change.state(), k -> new ArrayList<>())
                        .add(Math.max(0, end - change.time()));
            }
        }
        return result;
    }
}
