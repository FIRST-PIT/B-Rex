package dev.brex.testing.suite;

import dev.brex.analysis.regression.RegressionDetector;
import dev.brex.analysis.regression.RegressionReport;
import dev.brex.analysis.score.AutonScore;
import dev.brex.analysis.score.AutonScorer;
import dev.brex.core.run.Run;
import dev.brex.testing.project.BrexProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs regression checks for every routine that has a baseline: the engine behind
 * {@code brex test}.
 *
 * <p>For each routine, the candidate is its most recent recorded run. When that run is the
 * baseline itself there is nothing new to test and the routine is skipped.
 */
public final class RegressionSuite {

    /** Outcome for one routine. */
    public enum Status { PASSED, FAILED, SKIPPED, ERROR }

    /**
     * @param routine routine name
     * @param status outcome
     * @param baseline the baseline run, or null when missing
     * @param candidate the run under test, or null when missing
     * @param report regression checks, or null when not run
     * @param score autonomous score of the candidate, or null when not run
     * @param message explanation for skipped and errored routines, else empty
     */
    public record RoutineResult(String routine, Status status, Run baseline, Run candidate, RegressionReport report,
            AutonScore score, String message) {
    }

    /** Results for all routines. */
    public record Result(List<RoutineResult> routines) {

        public Result {
            routines = List.copyOf(routines);
        }

        /** True when no routine failed or errored (skips do not fail a suite). */
        public boolean passed() {
            return routines.stream().noneMatch(r -> r.status() == Status.FAILED || r.status() == Status.ERROR);
        }

        public long count(Status status) {
            return routines.stream().filter(r -> r.status() == status).count();
        }
    }

    private RegressionSuite() {
    }

    /**
     * Tests the given routines, or every routine with a baseline when {@code routines} is empty.
     *
     * @param candidateOverride run to test instead of the latest run; only valid for one routine
     */
    public static Result run(BrexProject project, List<String> routines, Run candidateOverride) {
        List<String> selected = routines.isEmpty() ? baselineRoutines(project) : routines;
        if (candidateOverride != null && selected.size() != 1) {
            throw new IllegalArgumentException("A specific run can only be tested against one routine's baseline");
        }
        List<RoutineResult> results = new ArrayList<>();
        for (String routine : selected) {
            results.add(testRoutine(project, routine, candidateOverride));
        }
        return new Result(results);
    }

    private static RoutineResult testRoutine(BrexProject project, String routine, Run candidateOverride) {
        Optional<Run> baseline;
        try {
            baseline = project.baselines().load(routine);
        } catch (IOException | RuntimeException e) {
            return new RoutineResult(routine, Status.ERROR, null, null, null, null,
                    "could not read baseline: " + e.getMessage());
        }
        if (baseline.isEmpty()) {
            return new RoutineResult(routine, Status.ERROR, null, null, null, null,
                    "no baseline; save one with 'brex baseline save " + routine + "'");
        }
        List<Run> history = project.history(routine);
        Run candidate = candidateOverride != null ? candidateOverride
                : history.isEmpty() ? null : history.get(history.size() - 1);
        if (candidate == null) {
            return new RoutineResult(routine, Status.ERROR, baseline.get(), null, null, null,
                    "no recorded runs to test; import or pull a run of " + routine);
        }
        if (candidate.id().equals(baseline.get().id())) {
            return new RoutineResult(routine, Status.SKIPPED, baseline.get(), candidate, null, null,
                    "the latest run is the baseline itself; record a new run to test");
        }
        RegressionReport report = new RegressionDetector(project.regressionThresholds(routine), project.idleStates())
                .detect(baseline.get(), candidate);
        List<Run> scoredHistory = history.stream()
                .filter(r -> r.metadata().startedAtEpochMillis() <= candidate.metadata().startedAtEpochMillis())
                .toList();
        AutonScore score = new AutonScorer(project.scoringConfig(routine), project.idleStates())
                .score(candidate, baseline.get(), scoredHistory);
        return new RoutineResult(routine, report.passed() ? Status.PASSED : Status.FAILED, baseline.get(), candidate,
                report, score, "");
    }

    private static List<String> baselineRoutines(BrexProject project) {
        try {
            List<String> names = new ArrayList<>();
            for (String slug : project.baselines().routines()) {
                // The file name is a slug; the routine name inside the baseline is authoritative.
                names.add(project.baselines().load(slug).map(Run::name).orElse(slug));
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
