package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.config.BrexConfig;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class InitCommandTest extends CommandTestBase {

    @Test
    void createsProjectOnce() throws IOException {
        CliHarness.Result first = cli.run("init");
        CliHarness.Result second = cli.run("init");

        assertEquals(ExitCode.OK, first.exitCode());
        assertTrue(first.out().contains("Initialized B-rex project in " + root), first.out());
        assertTrue(Files.isRegularFile(root.resolve("brex.properties")));
        assertTrue(Files.isDirectory(root.resolve(".brex/baselines")));
        assertTrue(Files.isDirectory(root.resolve(".brex/runs")));
        assertEquals("# Recorded runs are local; baselines are committed.\nruns/\n",
                Files.readString(root.resolve(".brex/.gitignore")));
        assertTrue(second.out().contains("already initialized"), second.out());
    }

    @Test
    void forceResetsConfiguration() throws IOException {
        cli.run("init");
        Files.writeString(root.resolve("brex.properties"), "units.distance = in\n");

        cli.run("init", "--force");

        assertTrue(Files.readString(root.resolve("brex.properties")).contains("units.distance = cm"));
    }

    @Test
    void generatedConfigurationIsValidAndComplete() throws IOException {
        cli.run("init");

        BrexConfig config = BrexConfig.load(root.resolve("brex.properties").toFile());

        assertTrue(config.unknownKeys().isEmpty(), config.unknownKeys().toString());
        assertEquals(0.025, config.meters("regression.maxFinalPositionError", 0), 1e-12);
    }
}
