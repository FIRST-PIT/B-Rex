package dev.brex.cli;

/**
 * A command-line option.
 *
 * @param name long name without dashes, e.g. {@code runs}
 * @param shortName single-letter alias without dash, or null
 * @param valueName placeholder for the value, e.g. {@code N}; null for boolean flags
 * @param description one-line help text
 */
public record OptionSpec(String name, String shortName, String valueName, String description) {

    public static OptionSpec flag(String name, String description) {
        return new OptionSpec(name, null, null, description);
    }

    public static OptionSpec value(String name, String valueName, String description) {
        return new OptionSpec(name, null, valueName, description);
    }

    public boolean takesValue() {
        return valueName != null;
    }

    /** The option as shown in help, e.g. {@code -q, --quiet} or {@code --runs <N>}. */
    public String signature() {
        String base = (shortName != null ? "-" + shortName + ", " : "") + "--" + name;
        return takesValue() ? base + " <" + valueName + ">" : base;
    }
}
