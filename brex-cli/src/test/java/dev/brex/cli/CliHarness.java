package dev.brex.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs the CLI in-process and captures its output. */
public final class CliHarness {

    public record Result(int exitCode, String out, String err) {
    }

    private final List<Command> commands;
    private final Path workingDirectory;
    private final Map<String, String> env = new HashMap<>();

    public CliHarness(List<Command> commands, Path workingDirectory) {
        this.commands = commands;
        this.workingDirectory = workingDirectory;
    }

    public CliHarness env(String key, String value) {
        env.put(key, value);
        return this;
    }

    public Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8);
        int code = new BrexCli(commands, outStream, errStream, env, workingDirectory, false).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
