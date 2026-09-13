package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.run.RunStatus;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class BaselineCommandTest extends CommandTestBase {

    @Test
    void savesLatestRunByDefault() throws IOException {
        importTwoRuns();

        CliHarness.Result saved = cli.run("baseline", "save", "blue-left");

        assertEquals(ExitCode.OK, saved.exitCode(), saved.err());
        assertTrue(saved.out().contains("Saved baseline blue-left from run " + SLOWER), saved.out());
        assertTrue(saved.out().contains(".brex/baselines/blue-left.run.json"));
        assertTrue(Files.isRegularFile(root.resolve(".brex/baselines/blue-left.run.json")));
    }

    @Test
    void listsShowsAndDeletesBaselines() throws IOException {
        importTwoRuns();
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result list = cli.run("baseline", "list");
        CliHarness.Result show = cli.run("baseline", "show", "blue-left");
        CliHarness.Result delete = cli.run("baseline", "delete", "blue-left");

        assertTrue(list.out().contains("blue-left         " + BASELINE), list.out());
        assertTrue(show.out().contains("RUN #1  blue-left"), show.out());
        assertEquals(ExitCode.OK, delete.exitCode());
        assertFalse(Files.exists(root.resolve(".brex/baselines/blue-left.run.json")));
    }

    @Test
    void refusesIncompleteRunsUnlessForced() throws IOException {
        cli.run("init");
        CliFixtures.write(downloads, CliFixtures.blueLeft(BASELINE, 1, 30, 0, RunStatus.INCOMPLETE));
        cli.run("run", "import", downloads.toString());

        CliHarness.Result refused = cli.run("baseline", "save", "blue-left");
        CliHarness.Result forced = cli.run("baseline", "save", "blue-left", "--force");

        assertEquals(ExitCode.ERROR, refused.exitCode());
        assertTrue(refused.err().contains("a baseline should be a known-good run"), refused.err());
        assertEquals(ExitCode.OK, forced.exitCode());
    }

    @Test
    void refusesRunOfAnotherRoutine() throws IOException {
        importTwoRuns();

        CliHarness.Result result = cli.run("baseline", "save", "red-right", "--run", "aaaa");

        assertEquals(ExitCode.ERROR, result.exitCode());
        assertTrue(result.err().contains("is a run of 'blue-left', not 'red-right'"), result.err());
    }

    @Test
    void unknownActionIsUsageError() throws IOException {
        cli.run("init");

        assertEquals(ExitCode.USAGE, cli.run("baseline", "promote", "blue-left").exitCode());
    }
}
