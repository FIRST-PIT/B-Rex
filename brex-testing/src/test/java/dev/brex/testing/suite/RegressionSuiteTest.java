package dev.brex.testing.suite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.trajectory.Trajectory;
import dev.brex.testing.project.BrexProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionSuiteTest {

    @TempDir
    Path root;

    static Run run(String id, String routine, long startedAt, double duration) {
        return Run.builder(id, RunMetadata.builder(routine).startedAtEpochMillis(startedAt).build())
                .duration(duration)
                .trajectory(new Trajectory.Builder().add(0, Pose.ORIGIN).add(duration, Pose.of(1, 1, 0)).build())
                .event(Event.of(duration / 2, "deposit.complete"))
                .build();
    }

    @Test
    void passesFailsAndSkipsPerRoutine() throws IOException {
        BrexProject project = BrexProject.at(root);
        project.runs().save(run("b1", "blue-left", 1, 22.31));
        project.baselines().save(project.latestRun("blue-left"));
        project.runs().save(run("b2", "blue-left", 2, 23.15));
        project.runs().save(run("r1", "red-right", 1, 20.0));
        project.baselines().save(project.latestRun("red-right"));
        project.runs().save(run("r2", "red-right", 2, 20.1));
        project.runs().save(run("g1", "far-park", 1, 10));
        project.baselines().save(project.latestRun("far-park"));

        RegressionSuite.Result result = RegressionSuite.run(project, List.of(), null);

        assertEquals(List.of("blue-left", "far-park", "red-right"),
                result.routines().stream().map(RegressionSuite.RoutineResult::routine).toList());
        assertEquals(RegressionSuite.Status.FAILED, result.routines().get(0).status());
        assertEquals(RegressionSuite.Status.SKIPPED, result.routines().get(1).status());
        assertEquals(RegressionSuite.Status.PASSED, result.routines().get(2).status());
        assertFalse(result.passed());
        assertEquals(0.84, result.routines().get(0).report().check("duration").delta(), 1e-9);
    }

    @Test
    void honorsConfiguredThresholds() throws IOException {
        Files.writeString(root.resolve("brex.properties"), "routine.blue-left.regression.maxDurationIncrease = 1s\n");
        BrexProject project = BrexProject.at(root);
        project.runs().save(run("b1", "blue-left", 1, 22.31));
        project.baselines().save(project.latestRun("blue-left"));
        project.runs().save(run("b2", "blue-left", 2, 23.15));

        assertTrue(RegressionSuite.run(project, List.of(), null).passed());
    }

    @Test
    void missingBaselineOrRunsIsAnError() throws IOException {
        BrexProject project = BrexProject.at(root);
        project.baselines().save(run("x1", "orphan", 1, 10));

        RegressionSuite.Result result = RegressionSuite.run(project, List.of("orphan", "unknown"), null);

        assertEquals(RegressionSuite.Status.ERROR, result.routines().get(0).status());
        assertEquals("no recorded runs to test; import or pull a run of orphan", result.routines().get(0).message());
        assertEquals("no baseline; save one with 'brex baseline save unknown'", result.routines().get(1).message());
        assertFalse(result.passed());
    }

    @Test
    void testsSpecificRunWhenGiven() throws IOException {
        BrexProject project = BrexProject.at(root);
        project.runs().save(run("b1", "blue-left", 1, 22.31));
        project.baselines().save(project.latestRun("blue-left"));

        RegressionSuite.Result result = RegressionSuite.run(project, List.of("blue-left"),
                run("candidate", "blue-left", 5, 22.4));

        assertEquals(RegressionSuite.Status.PASSED, result.routines().get(0).status());
        assertEquals("candidate", result.routines().get(0).candidate().id());
    }
}
