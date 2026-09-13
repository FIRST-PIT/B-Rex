package dev.brex.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parsed command-line arguments for one command. */
public final class Arguments {

    private final List<String> positionals;
    private final Map<String, String> values;
    private final Set<String> flags;

    private Arguments(List<String> positionals, Map<String, String> values, Set<String> flags) {
        this.positionals = List.copyOf(positionals);
        this.values = Map.copyOf(values);
        this.flags = Set.copyOf(flags);
    }

    /**
     * Parses tokens against the allowed options. Supports {@code --name value},
     * {@code --name=value}, {@code -n value}, flags, and {@code --} to end option parsing.
     */
    public static Arguments parse(List<String> tokens, List<OptionSpec> specs) {
        List<String> positionals = new ArrayList<>();
        Map<String, String> values = new HashMap<>();
        Set<String> flags = new HashSet<>();
        boolean optionsEnded = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (optionsEnded || !token.startsWith("-") || token.equals("-") || isNumber(token)) {
                positionals.add(token);
                continue;
            }
            if (token.equals("--")) {
                optionsEnded = true;
                continue;
            }
            String name;
            String inlineValue = null;
            if (token.startsWith("--")) {
                name = token.substring(2);
                int eq = name.indexOf('=');
                if (eq >= 0) {
                    inlineValue = name.substring(eq + 1);
                    name = name.substring(0, eq);
                }
            } else {
                name = token.substring(1);
            }
            OptionSpec spec = find(specs, name, token.startsWith("--"));
            if (spec == null) {
                throw CliException.usage("Unknown option '" + token + "'" + suggestion(name, specs));
            }
            if (spec.takesValue()) {
                String value = inlineValue;
                if (value == null) {
                    if (i + 1 >= tokens.size()) {
                        throw CliException.usage("Option --" + spec.name() + " requires a value <" + spec.valueName()
                                + ">");
                    }
                    value = tokens.get(++i);
                }
                values.put(spec.name(), value);
            } else {
                if (inlineValue != null) {
                    throw CliException.usage("Option --" + spec.name() + " does not take a value");
                }
                flags.add(spec.name());
            }
        }
        return new Arguments(positionals, values, flags);
    }

    public List<String> positionals() {
        return positionals;
    }

    /** The positional at {@code index}, or a usage error naming it. */
    public String positional(int index, String name) {
        if (index >= positionals.size()) {
            throw CliException.usage("Missing argument <" + name + ">");
        }
        return positionals.get(index);
    }

    public Optional<String> optionalPositional(int index) {
        return index < positionals.size() ? Optional.of(positionals.get(index)) : Optional.empty();
    }

    /** Fails when more than {@code max} positionals were given. */
    public Arguments requireAtMostPositionals(int max) {
        if (positionals.size() > max) {
            throw CliException.usage("Unexpected argument '" + positionals.get(max) + "'");
        }
        return this;
    }

    public boolean flag(String name) {
        return flags.contains(name);
    }

    public Optional<String> value(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public String value(String name, String fallback) {
        return values.getOrDefault(name, fallback);
    }

    public int intValue(String name, int fallback) {
        String raw = values.get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw CliException.usage("Option --" + name + " expects a whole number, got '" + raw + "'");
        }
    }

    public long longValue(String name, long fallback) {
        String raw = values.get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw CliException.usage("Option --" + name + " expects a whole number, got '" + raw + "'");
        }
    }

    public double doubleValue(String name, double fallback) {
        String raw = values.get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw CliException.usage("Option --" + name + " expects a number, got '" + raw + "'");
        }
    }

    private static OptionSpec find(List<OptionSpec> specs, String name, boolean longForm) {
        for (OptionSpec spec : specs) {
            if (longForm ? spec.name().equals(name) : name.equals(spec.shortName())) {
                return spec;
            }
        }
        return null;
    }

    private static boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String suggestion(String name, List<OptionSpec> specs) {
        List<String> names = specs.stream().map(OptionSpec::name).toList();
        return Suggestions.closest(name.toLowerCase(Locale.ROOT), names).map(s -> ". Did you mean --" + s + "?")
                .orElse("");
    }
}
