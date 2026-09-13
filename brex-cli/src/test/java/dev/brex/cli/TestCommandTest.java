package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.json.Json;
import dev.brex.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class TestCommandTest extends CommandTestBase {

    @Test
    void detectsRegression() throws IOException {
        importTwoRuns();
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result test = cli.run("test");

        assertEquals(ExitCode.FAILED, test.exitCode(), test.out() + test.err());
        assertTrue(test.out().contains("✗ blue-left  REGRESSION DETECTED"), test.out());
        assertTrue(test.out().contains("Previous: 22.31s"));
        assertTrue(test.out().contains("Current:  23.15s"));
        assertTrue(test.out().contains("+0.84s slower"));
        assertTrue(test.out().contains("✗ Duration           +0.84s slower than baseline  (limit 0.50s)"), test.out());
        assertTrue(test.out().contains("Score "));
        assertTrue(test.out().contains("✗ Tests failed"));
    }

    @Test
    void passesWithinConfiguredThresholds() throws IOException {
        importTwoRuns();
        Files.writeString(root.resolve("brex.properties"), """
                regression.maxDurationIncrease = 1s
                regression.maxEventDelay = 1s
                regression.maxFinalPositionError = 5cm
                """);
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result test = cli.run("test", "--quiet");

        assertEquals(ExitCode.OK, test.exitCode(), test.out());
        assertEquals("✓ blue-left  passed\n" + Fmt.rule(40) + "\n1 passed, 0 failed, 0 skipped\n✓ Tests passed\n",
                test.out());
    }

    @Test
    void writesJsonForCi() throws IOException {
        importTwoRuns();
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result test = cli.run("test", "--ci", "--format", "json");
        JsonObject json = Json.parseObject(test.out());

        assertEquals(ExitCode.FAILED, test.exitCode());
        assertFalse(json.getBoolean("passed"));
        assertEquals("test", json.getString("command"));
        assertEquals(1, json.getObject("summary").getInt("failed"));
        JsonObject routine = json.getObjectList("routines").get(0);
        assertEquals("FAILED", routine.getString("status"));
        assertEquals(SLOWER, routine.getObject("candidate").getString("id"));
        JsonObject duration = routine.getObjectList("checks").get(0);
        assertEquals("duration", duration.getString("id"));
        assertEquals(0.84, duration.getDouble("delta"), 1e-9);
    }

    @Test
    void testsSpecificRun() throws IOException {
        importTwoRuns();
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result test = cli.run("test", "--run", "aaaa", "--quiet");

        assertEquals(ExitCode.OK, test.exitCode(), test.out());
        assertTrue(test.out().contains("- blue-left  skipped: the latest run is the baseline itself"), test.out());
    }

    @Test
    void failsWhenThereIsNothingToTest() {
        cli.run("init");

        CliHarness.Result test = cli.run("test");

        assertEquals(ExitCode.FAILED, test.exitCode());
        assertTrue(test.err().contains("Nothing to test"), test.err());
    }

    @Test
    void explainsHowToStartOutsideProject() {
        CliHarness.Result result = cli.run("test");

        assertEquals(ExitCode.ERROR, result.exitCode());
        assertTrue(result.err().contains("Not a B-rex project"), result.err());
        assertTrue(result.err().contains("Run 'brex init' first."));
    }

    @Test
    void warnsAboutUnknownConfigKeys() throws IOException {
        importTwoRuns();
        Files.writeString(root.resolve("brex.properties"), "regresion.maxDurationIncrease = 1s\n");
        cli.run("baseline", "save", "blue-left", "--run", "aaaa");

        CliHarness.Result test = cli.run("test", "--quiet");

        assertTrue(test.err().contains("warning: unknown key 'regresion.maxDurationIncrease'"), test.err());
    }
}
