package dev.brex.core.telemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * All telemetry channels of a run, keyed by name.
 *
 * <p>There is no fixed schema: teams record whatever keys they need. B-rex's own recorders follow
 * a few conventions so that analysis can find common signals:
 *
 * <ul>
 *   <li>{@code motor.<name>.power|velocity|position|current}</li>
 *   <li>{@code servo.<name>.position}</li>
 *   <li>{@code encoder.<name>.position|velocity}</li>
 *   <li>{@code sensor.<name>.<reading>}</li>
 *   <li>{@code localization.error} — distance between estimated and reference pose, in meters</li>
 *   <li>{@code robot.batteryVoltage}</li>
 * </ul>
 */
public final class Telemetry {

    private static final Telemetry EMPTY = new Telemetry(Collections.<TelemetryChannel>emptyList());

    private final Map<String, TelemetryChannel> channels;

    public Telemetry(Collection<TelemetryChannel> channels) {
        Map<String, TelemetryChannel> sorted = new TreeMap<>();
        for (TelemetryChannel channel : channels) {
            if (sorted.put(channel.key(), channel) != null) {
                throw new IllegalArgumentException("Duplicate telemetry channel '" + channel.key() + "'");
            }
        }
        this.channels = Collections.unmodifiableMap(sorted);
    }

    public static Telemetry empty() {
        return EMPTY;
    }

    public boolean has(String key) {
        return channels.containsKey(key);
    }

    /** Returns the channel, or null when the run never recorded this key. */
    public TelemetryChannel channel(String key) {
        return channels.get(key);
    }

    /** Returns the channel or throws an error listing similar keys. */
    public TelemetryChannel require(String key) {
        TelemetryChannel channel = channels.get(key);
        if (channel == null) {
            throw new IllegalArgumentException("No telemetry recorded for '" + key + "'" + suggestionsFor(key));
        }
        return channel;
    }

    /** Channel keys in sorted order. */
    public Collection<String> keys() {
        return channels.keySet();
    }

    public Collection<TelemetryChannel> channels() {
        return channels.values();
    }

    /** Keys that start with {@code prefix}, such as {@code motor.}. */
    public List<String> keysWithPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        for (String key : channels.keySet()) {
            if (key.startsWith(prefix)) {
                result.add(key);
            }
        }
        return result;
    }

    public int size() {
        return channels.size();
    }

    private String suggestionsFor(String key) {
        int dot = key.indexOf('.');
        String namespace = dot > 0 ? key.substring(0, dot + 1) : key;
        List<String> similar = keysWithPrefix(namespace);
        if (similar.isEmpty()) {
            return channels.isEmpty() ? " (the run has no telemetry)" : "";
        }
        if (similar.size() > 5) {
            similar = similar.subList(0, 5);
        }
        return " (recorded keys include: " + similar + ")";
    }
}
