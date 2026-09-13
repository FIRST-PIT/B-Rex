package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.json.Json;
import dev.brex.core.run.RunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunCommandTest extends CommandTestBase {

    @Test
    void importsAndListsRuns() throws IOException {
        importTwoRuns();

        CliHarness.Result list = cli.run("run", "list");
        CliHarness.Result again = cli.run("run", "import", downloads.toString());

        assertTrue(list.out().contains("   1  " + BASELINE), list.out());
        assertTrue(list.out().contains("   2  " + SLOWER));
        assertTrue(list.out().contains("a91f42c"));
        assertEquals("", list.err());
        assertTrue(again.out().contains("Imported 0 run(s), 2 already present"), again.out());
    }

    @Test
    void listFiltersByRoutineAndLimits() throws IOException {
        importTwoRuns();

        CliHarness.Result other = cli.run("run", "list", "red-right");
        CliHarness.Result limited = cli.run("run", "list", "--limit", "1");

        assertTrue(other.out().startsWith("No runs of 'red-right'."), other.out());
        assertFalse(limited.out().contains(BASELINE), limited.out());
        assertTrue(limited.out().contains("1 older run(s) not shown"));
    }

    @Test
    void importReportsUnreadableFiles() throws IOException {
        cli.run("init");
        Files.writeString(downloads.resolve("broken.run.json"), "{\"format\": \"brex-run\"");

        CliHarness.Result result = cli.run("run", "import", downloads.toString());

        assertEquals(ExitCode.ERROR, result.exitCode());
        assertTrue(result.err().contains("broken.run.json"), result.err());
    }

    @Test
    void importCanStampCommit() throws IOException {
        cli.run("init");
        CliFixtures.write(downloads, CliFixtures.blueLeft(BASELINE, 1, 22.31, 0, RunStatus.COMPLETED));

        cli.run("run", "import", downloads.toString(), "--commit", "0123456789");
        CliHarness.Result show = cli.run("run", "show", "latest", "--format", "json");

        assertEquals("0123456789", Json.parseObject(show.out()).getObject("run").getString("gitCommit"));
    }

    @Test
    void showsRunWithScore() throws IOException {
        importTwoRuns();

        CliHarness.Result result = cli.run("run", "show", "#1");

        assertTrue(result.out().contains("RUN #1  blue-left"), result.out());
        assertTrue(result.out().contains("Status     completed in 22.31s"));
        assertTrue(result.out().contains("Commit     a91f42c"));
        assertTrue(result.out().contains("AUTON SCORE"));
        assertTrue(result.out().contains("TOTAL"));
    }

    @Test
    void unknownRunReferenceIsExplained() throws IOException {
        importTwoRuns();

        CliHarness.Result result = cli.run("run", "show", "zzzz");

        assertEquals(ExitCode.ERROR, result.exitCode());
        assertTrue(result.err().contains("No run matches 'zzzz'"), result.err());
    }

    @Test
    void pullsRunsWithAdb() {
        cli.run("init");

        CliHarness.Result pull = cli.run("run", "pull", "--adb", "/opt/adb");

        assertEquals(ExitCode.OK, pull.exitCode(), pull.err());
        assertEquals(List.of("/opt/adb", "pull", "/sdcard/FIRST/brex/runs/."), processCalls.get(0).subList(0, 3));
        assertTrue(pull.out().contains("+ " + PULLED), pull.out());
        assertTrue(pull.out().contains("Imported 1 run(s)"));
        assertFalse(Files.exists(root.resolve(".brex/pull")), "staging directory is cleaned up");
    }

    @Test
    void pullExplainsAdbFailures() {
        CliHarness failing = harness((command, directory) ->
                new ProcessRunner.Result(1, "adb: error: no devices/emulators found"));
        failing.run("init");

        CliHarness.Result pull = failing.run("run", "pull");

        assertEquals(ExitCode.ERROR, pull.exitCode());
        assertTrue(pull.err().contains("no devices/emulators found"), pull.err());
        assertTrue(pull.err().contains("adb connect 192.168.43.1:5555"));
    }

    @Test
    void pullExplainsMissingAdb() {
        CliHarness missing = harness((command, directory) -> {
            throw new IOException("Cannot run program \"adb\"");
        });
        missing.run("init");

        CliHarness.Result pull = missing.run("run", "pull");

        assertTrue(pull.err().contains("Install Android platform-tools or pass --adb <path>"), pull.err());
    }
}
