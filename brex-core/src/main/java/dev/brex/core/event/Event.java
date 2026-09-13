package dev.brex.core.event;

import dev.brex.core.util.Names;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named moment in a run, such as {@code intake.start} or {@code deposit.complete}.
 *
 * <p>Events carry optional string attributes (for example {@code points=2}) and are the backbone
 * of ordering and timing assertions.
 */
public final class Event {

    /** Attribute holding game points scored at this event. */
    public static final String POINTS_ATTRIBUTE = "points";

    private final double time;
    private final String name;
    private final Map<String, String> attributes;

    private Event(double time, String name, Map<String, String> attributes) {
        this.time = time;
        this.name = Names.requireValid("Event name", name);
        this.attributes = attributes;
    }

    public static Event of(double time, String name) {
        return new Event(time, name, Collections.<String, String>emptyMap());
    }

    public static Event of(double time, String name, Map<String, String> attributes) {
        return new Event(time, name, Collections.unmodifiableMap(new LinkedHashMap<>(attributes)));
    }

    /** Seconds since the start of the run. */
    public double time() {
        return time;
    }

    public String name() {
        return name;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public String attribute(String key) {
        return attributes.get(key);
    }

    /** Game points attached to this event, or 0 when it has none. */
    public double points() {
        String value = attributes.get(POINTS_ATTRIBUTE);
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Event '" + name + "' has non-numeric points '" + value + "'");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Event)) {
            return false;
        }
        Event other = (Event) o;
        return Double.compare(time, other.time) == 0 && name.equals(other.name) && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Double.hashCode(time) + name.hashCode()) + attributes.hashCode();
    }

    @Override
    public String toString() {
        return "Event(" + time + "s, " + name + (attributes.isEmpty() ? "" : ", " + attributes) + ")";
    }
}
