package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.json.Json;
import dev.brex.core.json.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ReplayCommandTest extends CommandTestBase {

    @Test
    void printsTimelineWithPoses() throws IOException {
        importTwoRuns();

        CliHarness.Result replay = cli.run("replay", "latest", "--step", "10");

        assertTrue(replay.out().contains("RUN #2  blue-left"), replay.out());
        assertTrue(replay.out().contains("   4.63s  ● alignment.complete"), replay.out());
        assertTrue(replay.out().contains("  11.58s  ◆ lift        IDLE → RAISING"), replay.out());
        assertTrue(replay.out().contains("   0.00s  · pose (0.0cm, 2.1cm, 0.0°)"), replay.out());
        assertTrue(replay.out().contains("  23.15s  ■ end (completed)"));
    }

    @Test
    void filtersByKind() throws IOException {
        importTwoRuns();

        CliHarness.Result events = cli.run("replay", "1", "--only", "events");
        CliHarness.Result invalid = cli.run("replay", "1", "--only", "sounds");

        assertTrue(events.out().contains("● deposit.complete  points=4"), events.out());
        assertFalse(events.out().contains("◆"));
        assertEquals(ExitCode.USAGE, invalid.exitCode());
        assertTrue(invalid.err().contains("Unknown kind 'sounds'"), invalid.err());
    }

    @Test
    void replaysRunFileOutsideProject() throws IOException {
        java.nio.file.Path file = CliFixtures.write(downloads, CliFixtures.blueLeft(BASELINE, 1, 22.31, 0,
                dev.brex.core.run.RunStatus.COMPLETED));

        CliHarness.Result replay = cli.run("replay", file.toString(), "--from", "10", "--to", "12");

        assertEquals(ExitCode.OK, replay.exitCode(), replay.err());
        assertTrue(replay.out().contains("RUN  blue-left"), replay.out());
        assertTrue(replay.out().contains("◆ lift        IDLE → RAISING"));
        assertFalse(replay.out().contains("alignment.complete"));
    }

    @Test
    void writesJson() throws IOException {
        importTwoRuns();

        JsonObject json = Json.parseObject(cli.run("replay", "1", "--format", "json", "--step", "11.155").out());

        assertEquals(BASELINE, json.getObject("run").getString("id"));
        assertEquals(6, json.getObjectList("timeline").size());
        assertEquals("RAISING", json.getObjectList("snapshots").get(1).getObject("mechanismStates")
                .getString("lift"));
    }
}
