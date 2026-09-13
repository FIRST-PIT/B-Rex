package dev.brex.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * Everything a command needs from its environment. Commands never touch {@code System.out},
 * {@code System.getenv()} or the working directory directly, which keeps them testable.
 *
 * @param out standard output
 * @param err standard error
 * @param style terminal styling
 * @param workingDirectory where the command was invoked (or {@code --project})
 * @param format requested output format
 * @param quiet print only essential output
 * @param ci running in continuous integration: no color, no banner, machine-friendly
 * @param env environment variables
 */
public record CommandContext(
        PrintStream out,
        PrintStream err,
        Style style,
        Path workingDirectory,
        OutputFormat format,
        boolean quiet,
        boolean ci,
        Map<String, String> env) {

    public boolean json() {
        return format == OutputFormat.JSON;
    }

    /** Prints a line unless {@code --quiet} or JSON output was requested. */
    public void info(String line) {
        if (!quiet && !json()) {
            out.println(line);
        }
    }
}
