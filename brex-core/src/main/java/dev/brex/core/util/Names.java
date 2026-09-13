package dev.brex.core.util;

/** Validation for identifiers such as event names and mechanism names. */
public final class Names {

    private Names() {
    }

    /**
     * Requires a non-empty name without whitespace. Dots are encouraged as namespace separators,
     * for example {@code intake.start}.
     */
    public static String requireValid(String kind, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(kind + " must not be empty");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                throw new IllegalArgumentException(kind + " '" + name + "' must not contain whitespace");
            }
        }
        return name;
    }
}
