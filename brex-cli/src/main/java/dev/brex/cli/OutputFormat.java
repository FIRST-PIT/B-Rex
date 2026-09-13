package dev.brex.cli;

import java.util.Locale;

/** How a command writes its results. */
public enum OutputFormat {
    TEXT,
    JSON;

    public static OutputFormat parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "text" -> TEXT;
            case "json" -> JSON;
            default -> throw CliException.usage("Unknown format '" + value + "' (expected text or json)");
        };
    }
}
