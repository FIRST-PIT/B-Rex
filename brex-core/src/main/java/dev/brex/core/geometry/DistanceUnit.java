package dev.brex.core.geometry;

import java.util.Locale;

/** Distance units for input and display. B-rex stores all distances in meters. */
public enum DistanceUnit {
    METERS("m", 1.0),
    CENTIMETERS("cm", 0.01),
    MILLIMETERS("mm", 0.001),
    INCHES("in", 0.0254);

    private final String symbol;
    private final double meters;

    DistanceUnit(String symbol, double meters) {
        this.symbol = symbol;
        this.meters = meters;
    }

    public String symbol() {
        return symbol;
    }

    public double toMeters(double value) {
        return value * meters;
    }

    public double fromMeters(double valueMeters) {
        return valueMeters / meters;
    }

    /** Parses a unit symbol or name such as {@code cm}, {@code in} or {@code INCHES}. */
    public static DistanceUnit parse(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        for (DistanceUnit unit : values()) {
            if (unit.symbol.equals(normalized) || unit.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return unit;
            }
        }
        if (normalized.equals("inch")) {
            return INCHES;
        }
        throw new IllegalArgumentException("Unknown distance unit '" + text + "' (expected m, cm, mm or in)");
    }
}
