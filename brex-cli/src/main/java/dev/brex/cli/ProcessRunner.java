package dev.brex.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** Runs external programs (such as adb). Replaced by a fake in tests. */
public interface ProcessRunner {

    /**
     * @param exitCode the process exit code
     * @param output combined standard output and error
     */
    record Result(int exitCode, String output) {
    }

    Result run(List<String> command, Path directory) throws IOException, InterruptedException;

    /** Runs real processes. */
    static ProcessRunner system() {
        return (command, directory) -> {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output);
        };
    }
}
