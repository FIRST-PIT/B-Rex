package dev.brex.testing.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.analysis.regression.RegressionThresholds;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.config.ConfigException;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrexProjectTest {

    @TempDir
    Path root;

    private static Run run(String id, String name, long startedAt) {
        return Run.builder(id, RunMetadata.builder(name).startedAtEpochMillis(startedAt).build()).duration(20).build();
    }

    @Test
    void findsProjectRootFromSubdirectory() throws IOException {
        Files.writeString(root.resolve("brex.properties"), "");
        Path nested = Files.createDirectories(root.resolve("TeamCode/src/main"));

        BrexProject project = BrexProject.open(nested);

        assertEquals(root.toRealPath(), project.root().toRealPath());
        assertTrue(project.isInitialized());
    }

    @Test
    void fallsBackToStartDirectoryWhenNotInitialized() {
        BrexProject project = BrexProject.open(root);

        assertFalse(project.isInitialized());
        assertEquals(RegressionThresholds.DEFAULTS, project.regressionThresholds("blue-left"));
        assertEquals(ScoringConfig.DEFAULTS.historyWindow(), project.scoringConfig("blue-left").historyWindow());
    }

    @Test
    void bindsRegressionThresholdsWithRoutineOverrides() throws IOException {
        Files.writeString(root.resolve("brex.properties"), """
                regression.maxDurationIncrease = 0.4s
                regression.maxFinalPositionError = 1in
                regression.maxFinalHeadingError = 3deg
                routine.blue-left.regression.maxDurationIncrease = 250ms
                """);
        BrexProject project = BrexProject.at(root);

        RegressionThresholds blueLeft = project.regressionThresholds("blue-left");
        RegressionThresholds redRight = project.regressionThresholds("red-right");

        assertEquals(0.25, blueLeft.maxDurationIncrease(), 1e-12);
        assertEquals(0.4, redRight.maxDurationIncrease(), 1e-12);
        assertEquals(0.0254, redRight.maxFinalPositionError(), 1e-12);
        assertEquals(Math.toRadians(3), redRight.maxFinalHeadingError(), 1e-12);
        assertEquals(RegressionThresholds.DEFAULTS.maxEventDelay(), redRight.maxEventDelay());
    }

    @Test
    void bindsScoringConfig() throws IOException {
        Files.writeString(root.resolve("brex.properties"), """
                scoring.weight.speed = 2
                scoring.targetDuration = 22s
                scoring.historyWindow = 5
                """);

        ScoringConfig scoring = BrexProject.at(root).scoringConfig("blue-left");

        assertEquals(2.0, scoring.speedWeight());
        assertEquals(22.0, scoring.targetSeconds());
        assertEquals(5, scoring.historyWindow());
    }

    @Test
    void invalidConfigValuesNameTheFile() throws IOException {
        Files.writeString(root.resolve("brex.properties"), "scoring.historyWindow = 0\n");

        ConfigException error = assertThrows(ConfigException.class,
                () -> BrexProject.at(root).scoringConfig("blue-left"));

        assertTrue(error.getMessage().endsWith("brex.properties: historyWindow must be >= 1 (was 0)"),
                error.getMessage());
    }

    @Test
    void customIdleStates() throws IOException {
        Files.writeString(root.resolve("brex.properties"), "mechanisms.idleStates = PARKED, idle\n");

        BrexProject project = BrexProject.at(root);

        assertTrue(project.idleStates().isIdle("parked"));
        assertFalse(project.idleStates().isIdle("STOWED"));
    }

    @Test
    void latestRunExplainsHowToGetRuns() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BrexProject.at(root).latestRun("blue-left"));

        assertEquals("No runs of 'blue-left' in .brex/runs. Record one on the robot and copy it with 'brex run pull', "
                + "or import a file with 'brex run import <file>'.", error.getMessage());
    }

    @Test
    void savesAndLoadsBaselines() throws IOException {
        BrexProject project = BrexProject.at(root);
        project.runs().save(run("r1", "blue-left", 1));
        project.runs().save(run("r2", "blue-left", 2));

        Path file = project.baselines().save(project.latestRun("blue-left"));

        assertEquals(root.resolve(".brex/baselines/blue-left.run.json"), file);
        assertEquals("r2", project.baseline("blue-left").orElseThrow().id());
        assertEquals(List.of("blue-left"), project.baselines().routines());
        assertEquals(2, project.history("blue-left").size());
        assertTrue(project.baselines().delete("blue-left"));
        assertTrue(project.baseline("blue-left").isEmpty());
    }

    @Test
    void honorsCustomDirectories() throws IOException {
        Files.writeString(root.resolve("brex.properties"), "project.runsDir = data/runs\nproject.baselinesDir = ref\n");

        BrexProject project = BrexProject.at(root);

        assertEquals(root.resolve("data/runs").toFile(), project.runs().directory());
        assertEquals(root.resolve("ref"), project.baselines().directory());
    }
}
