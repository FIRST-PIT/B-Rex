package dev.brex.cli;

import dev.brex.cli.commands.BaselineCommand;
import dev.brex.cli.commands.InitCommand;
import dev.brex.cli.commands.RunCommand;
import java.util.List;

/** The built-in command set, in the order shown by {@code brex --help}. */
final class Commands {

    private Commands() {
    }

    static List<Command> all() {
        return all(ProcessRunner.system());
    }

    static List<Command> all(ProcessRunner processes) {
        return List.of(
                new InitCommand(),
                new RunCommand(processes),
                new BaselineCommand());
    }
}
