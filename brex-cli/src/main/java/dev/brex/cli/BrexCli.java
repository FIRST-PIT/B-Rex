package dev.brex.cli;

import dev.brex.core.BrexVersion;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses global options, dispatches to a {@link Command}, prints the banner and maps failures to
 * exit codes.
 */
public final class BrexCli {

    static final List<OptionSpec> GLOBAL_OPTIONS = List.of(
            new OptionSpec("help", "h", null, "Show help"),
            new OptionSpec("version", "V", null, "Print the B-rex version"),
            OptionSpec.flag("no-banner", "Do not print the startup banner"),
            OptionSpec.flag("no-color", "Disable colored output"),
            new OptionSpec("quiet", "q", null, "Print only essential output (implies --no-banner)"),
            OptionSpec.value("format", "text|json", "Output format (json implies --no-banner)"),
            OptionSpec.flag("ci", "CI mode: no banner, no color, machine-friendly output"),
            OptionSpec.value("project", "dir", "B-rex project directory (default: current directory)"));

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final PrintStream out;
    private final PrintStream err;
    private final Map<String, String> env;
    private final Path workingDirectory;
    private final boolean terminal;

    public BrexCli(List<Command> commands, PrintStream out, PrintStream err, Map<String, String> env,
            Path workingDirectory, boolean terminal) {
        for (Command command : commands) {
            this.commands.put(command.name(), command);
        }
        this.out = out;
        this.err = err;
        this.env = env;
        this.workingDirectory = workingDirectory;
        this.terminal = terminal;
    }

    /** The CLI wired to the real process environment and every built-in command. */
    public static BrexCli standard() {
        return new BrexCli(Commands.all(), System.out, System.err, System.getenv(),
                Path.of("").toAbsolutePath(), System.console() != null);
    }

    public int run(String... args) {
        List<String> tokens = Arrays.asList(args);
        int commandIndex = indexOfCommand(tokens);
        String commandName = commandIndex < 0 ? null : tokens.get(commandIndex);
        Command command = commandName == null ? null : commands.get(commandName);

        Arguments globals;
        Arguments arguments;
        List<OptionSpec> specs = new ArrayList<>(GLOBAL_OPTIONS);
        if (command != null) {
            specs.addAll(command.options());
        }
        List<String> rest = new ArrayList<>(tokens);
        if (commandIndex >= 0) {
            rest.remove(commandIndex);
        }
        try {
            arguments = Arguments.parse(rest, specs);
            globals = arguments;
        } catch (CliException e) {
            err.println("error: " + e.getMessage());
            err.println("Run 'brex " + (command != null ? command.name() + " " : "") + "--help' for usage.");
            return e.exitCode();
        }

        if (globals.flag("version")) {
            out.println("brex " + BrexVersion.get());
            return ExitCode.OK;
        }

        CommandContext context;
        try {
            context = context(globals);
        } catch (CliException e) {
            err.println("error: " + e.getMessage());
            return e.exitCode();
        }
        boolean showBanner = !globals.flag("no-banner") && !context.quiet() && !context.json() && !context.ci()
                && !env.containsKey("BREX_NO_BANNER");

        if (commandName != null && command == null) {
            if (commandName.equals("help")) {
                return help(context, showBanner, arguments.optionalPositional(0));
            }
            err.println("error: Unknown command '" + commandName + "'"
                    + Suggestions.closest(commandName, commands.keySet()).map(s -> ". Did you mean '" + s + "'?")
                            .orElse(""));
            err.println("Run 'brex --help' to see all commands.");
            return ExitCode.USAGE;
        }
        if (command == null) {
            return help(context, showBanner, Optional.empty());
        }
        if (globals.flag("help")) {
            printCommandHelp(command);
            return ExitCode.OK;
        }
        if (showBanner) {
            Banner.print(out, context.style(), BrexVersion.get());
        }
        try {
            return command.execute(context, arguments);
        } catch (CliException e) {
            err.println(context.style().red("error: ") + e.getMessage());
            if (e.exitCode() == ExitCode.USAGE) {
                err.println("Run 'brex " + command.name() + " --help' for usage.");
            }
            return e.exitCode();
        } catch (Exception e) {
            err.println(context.style().red("error: ") + describe(e));
            if (env.containsKey("BREX_DEBUG")) {
                StringWriter trace = new StringWriter();
                e.printStackTrace(new PrintWriter(trace));
                err.print(trace);
            } else {
                err.println("Set BREX_DEBUG=1 to see the stack trace.");
            }
            return ExitCode.ERROR;
        }
    }

    private CommandContext context(Arguments globals) {
        boolean ci = globals.flag("ci") || "true".equalsIgnoreCase(env.get("CI"));
        OutputFormat format = OutputFormat.parse(globals.value("format", "text"));
        boolean color = terminal && !ci && !globals.flag("no-color") && !env.containsKey("NO_COLOR");
        Path project = globals.value("project").map(p -> workingDirectory.resolve(p).normalize())
                .orElse(workingDirectory);
        return new CommandContext(out, err, color ? Style.ANSI : Style.PLAIN, project, format,
                globals.flag("quiet"), ci, env);
    }

    /** The first token that is neither an option nor an option's value. */
    private int indexOfCommand(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("--")) {
                return -1;
            }
            if (!token.startsWith("-")) {
                return i;
            }
            String name = token.startsWith("--") ? token.substring(2) : token.substring(1);
            boolean takesValue = GLOBAL_OPTIONS.stream().anyMatch(o -> o.takesValue()
                    && (o.name().equals(name) || name.equals(o.shortName())));
            if (takesValue && !name.contains("=")) {
                i++;
            }
        }
        return -1;
    }

    private int help(CommandContext context, boolean showBanner, Optional<String> topic) {
        if (topic.isPresent()) {
            Command command = commands.get(topic.get());
            if (command == null) {
                err.println("error: Unknown command '" + topic.get() + "'");
                return ExitCode.USAGE;
            }
            printCommandHelp(command);
            return ExitCode.OK;
        }
        if (showBanner) {
            Banner.print(out, context.style(), BrexVersion.get());
        }
        Style style = context.style();
        out.println(style.bold("Usage:") + " brex [options] <command> [arguments]");
        out.println();
        out.println(style.bold("Commands:"));
        int width = commands.keySet().stream().mapToInt(String::length).max().orElse(0);
        for (Command command : commands.values()) {
            out.println("  " + padRight(command.name(), width) + "  " + command.summary());
        }
        out.println();
        printOptions(style, "Global options:", GLOBAL_OPTIONS);
        out.println();
        out.println("Run 'brex <command> --help' for details on a command.");
        return ExitCode.OK;
    }

    private void printCommandHelp(Command command) {
        Style style = Style.PLAIN;
        out.println(style.bold("Usage:") + " " + command.usage());
        out.println();
        out.println(command.summary());
        if (!command.help().isEmpty()) {
            out.println();
            out.println(command.help().stripTrailing());
        }
        if (!command.options().isEmpty()) {
            out.println();
            printOptions(style, "Options:", command.options());
        }
        out.println();
        printOptions(style, "Global options:", GLOBAL_OPTIONS);
    }

    private void printOptions(Style style, String title, List<OptionSpec> options) {
        out.println(style.bold(title));
        int width = options.stream().mapToInt(o -> o.signature().length()).max().orElse(0);
        for (OptionSpec option : options) {
            out.println("  " + padRight(option.signature(), width) + "  " + option.description());
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    static String padRight(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
