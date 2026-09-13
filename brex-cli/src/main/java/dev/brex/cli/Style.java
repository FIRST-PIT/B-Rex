package dev.brex.cli;

/** Terminal styling that degrades to plain text when color is disabled. */
public final class Style {

    public static final Style PLAIN = new Style(false);
    public static final Style ANSI = new Style(true);

    private final boolean enabled;

    private Style(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public String bold(String text) {
        return wrap("1", text);
    }

    public String dim(String text) {
        return wrap("2", text);
    }

    public String green(String text) {
        return wrap("32", text);
    }

    public String red(String text) {
        return wrap("31", text);
    }

    public String yellow(String text) {
        return wrap("33", text);
    }

    public String cyan(String text) {
        return wrap("36", text);
    }

    /** A pass/fail marker: ✓ or ✗. */
    public String mark(boolean ok) {
        return ok ? green("✓") : red("✗");
    }

    private String wrap(String code, String text) {
        return enabled ? "[" + code + "m" + text + "[0m" : text;
    }
}
