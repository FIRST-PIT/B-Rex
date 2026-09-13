package dev.brex.core.event;

import dev.brex.core.util.Names;

/**
 * A mechanism entering a named state, for example {@code lift -> RAISING}.
 *
 * <p>A mechanism stays in a state until its next state change. State names are free-form strings
 * so any mechanism library (or plain enums) can be recorded.
 */
public final class MechanismStateChange {

    private final double time;
    private final String mechanism;
    private final String state;

    private MechanismStateChange(double time, String mechanism, String state) {
        this.time = time;
        this.mechanism = Names.requireValid("Mechanism name", mechanism);
        if (state == null || state.isEmpty()) {
            throw new IllegalArgumentException("State of mechanism '" + mechanism + "' must not be empty");
        }
        this.state = state;
    }

    public static MechanismStateChange of(double time, String mechanism, String state) {
        return new MechanismStateChange(time, mechanism, state);
    }

    public double time() {
        return time;
    }

    public String mechanism() {
        return mechanism;
    }

    public String state() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MechanismStateChange)) {
            return false;
        }
        MechanismStateChange other = (MechanismStateChange) o;
        return Double.compare(time, other.time) == 0 && mechanism.equals(other.mechanism) && state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Double.hashCode(time) + mechanism.hashCode()) + state.hashCode();
    }

    @Override
    public String toString() {
        return "MechanismStateChange(" + time + "s, " + mechanism + " -> " + state + ")";
    }
}
