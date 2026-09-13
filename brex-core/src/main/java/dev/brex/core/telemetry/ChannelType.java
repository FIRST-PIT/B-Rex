package dev.brex.core.telemetry;

import java.util.Locale;

/** The value type of a telemetry channel. A channel's type is fixed by its first sample. */
public enum ChannelType {
    NUMBER,
    BOOLEAN,
    TEXT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ChannelType fromId(String id) {
        for (ChannelType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown telemetry channel type '" + id + "'");
    }
}
