package dev.brex.analysis;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mechanism state names that mean "not doing anything". Idle states are excluded from mechanism
 * timing checks (a lift that waits longer in {@code IDLE} is a scheduling delay, not a slower
 * lift) and count as inactive in timing analysis. Matching is case-insensitive.
 *
 * @param names idle state names
 */
public record IdleStates(Set<String> names) {

    public static final IdleStates DEFAULT = of("IDLE", "OFF", "STOPPED", "STOWED", "HOME", "REST");

    public IdleStates {
        names = names.stream().map(n -> n.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static IdleStates of(String... names) {
        return new IdleStates(Set.of(names));
    }

    public boolean isIdle(String state) {
        return state != null && names.contains(state.toUpperCase(Locale.ROOT));
    }
}
