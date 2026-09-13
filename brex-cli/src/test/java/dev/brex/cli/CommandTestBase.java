package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.brex.core.run.RunStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** A temporary project directory and a CLI whose adb calls are faked. */
abstract class CommandTestBase {

    static final String BASELINE = "20260913-100000-blue-left-aaaa";
    static final String SLOWER = "20260913-110000-blue-left-bbbb";
    static final String PULLED = "20260913-120000-blue-left-cccc";

    @TempDir
    Path root;

    @TempDir
    Path downloads;

    final List<List<String>> processCalls = new ArrayList<>();
    CliHarness cli;

    @BeforeEach
    void createCli() {
        cli = harness((command, directory) -> {
            processCalls.add(command);
            CliFixtures.write(Path.of(command.get(3)), CliFixtures.blueLeft(PULLED, 3, 22.2, 0, RunStatus.COMPLETED));
            return new ProcessRunner.Result(0, "1 file pulled");
        });
    }

    CliHarness harness(ProcessRunner processes) {
        return new CliHarness(Commands.all(processes), root).env("BREX_NO_BANNER", "1");
    }

    /** Initializes the project and imports a 22.31s baseline-quality run and a slower 23.15s run. */
    void importTwoRuns() throws IOException {
        CliFixtures.write(downloads, CliFixtures.blueLeft(BASELINE, 1, 22.31, 0, RunStatus.COMPLETED));
        CliFixtures.write(downloads, CliFixtures.blueLeft(SLOWER, 2, 23.15, 0.021, RunStatus.COMPLETED));
        assertEquals(ExitCode.OK, cli.run("init").exitCode());
        CliHarness.Result imported = cli.run("run", "import", downloads.toString());
        assertEquals(ExitCode.OK, imported.exitCode(), imported.err());
    }
}
