package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.json.Json;
import dev.brex.core.json.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class CompareCommandTest extends CommandTestBase {

    @Test
    void showsGhostDifferences() throws IOException {
        importTwoRuns();

        CliHarness.Result compare = cli.run("compare", "1", "2");

        assertEquals(ExitCode.OK, compare.exitCode(), compare.err());
        assertTrue(compare.out().contains("RUN #1  vs  RUN #2   blue-left"), compare.out());
        assertTrue(compare.out().contains("Duration             +0.84s   22.31s → 23.15s"), compare.out());
        assertTrue(compare.out().contains("Path deviation"));
        assertTrue(compare.out().contains("  deposit.complete               15.62s    16.21s    +0.59s"),
                compare.out());
        assertTrue(compare.out().contains("  lift → RAISING"), compare.out());
    }

    @Test
    void includesSamplesInJsonOnRequest() throws IOException {
        importTwoRuns();

        JsonObject json = Json.parseObject(cli.run("compare", "1", "2", "--format", "json", "--samples").out());

        assertEquals(0.84, json.getDouble("durationDelta"), 1e-9);
        assertEquals(101, json.getObjectList("samples").size());
        assertEquals(3, json.getObjectList("events").size());
        assertEquals(3, json.getObjectList("mechanismStates").size());
        assertEquals("MATCHED", json.getObjectList("events").get(0).getString("status"));
    }

    @Test
    void warnsWhenRoutinesDiffer() throws IOException {
        importTwoRuns();
        java.nio.file.Path other = CliFixtures.write(downloads, dev.brex.core.run.Run.builder("x",
                dev.brex.core.run.RunMetadata.builder("red-right").build()).duration(5).build());

        CliHarness.Result compare = cli.run("compare", "1", other.toString());

        assertTrue(compare.err().contains("comparing runs of different routines (blue-left vs red-right)"),
                compare.err());
    }
}
