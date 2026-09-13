package dev.brex.cli;

import java.util.List;

/** A {@code brex} subcommand. */
public interface Command {

    /** The word typed after {@code brex}, e.g. {@code test}. */
    String name();

    /** One line for the command list. */
    String summary();

    /** Usage line(s), e.g. {@code brex replay <run> [options]}. */
    String usage();

    /** Longer help text shown by {@code brex <command> --help}; may include examples. */
    default String help() {
        return "";
    }

    /** Command-specific options. Global options are added automatically. */
    default List<OptionSpec> options() {
        return List.of();
    }

    /** Runs the command and returns a process exit code from {@link ExitCode}. */
    int execute(CommandContext context, Arguments arguments) throws Exception;
}
