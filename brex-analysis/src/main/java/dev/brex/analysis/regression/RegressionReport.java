package dev.brex.analysis.regression;

import dev.brex.core.run.TestResult;
import java.util.List;

/**
 * All regression checks for one routine.
 *
 * @param routine routine name, e.g. {@code blue-left}
 * @param baselineRunId the known-good run
 * @param candidateRunId the run under test
 * @param checks every check, including passes and skips
 */
public record RegressionReport(String routine, String baselineRunId, String candidateRunId,
        List<RegressionCheck> checks) {

    public RegressionReport {
        checks = List.copyOf(checks);
    }

    public boolean passed() {
        return checks.stream().noneMatch(RegressionCheck::failed);
    }

    public List<RegressionCheck> failures() {
        return checks.stream().filter(RegressionCheck::failed).toList();
    }

    public RegressionCheck check(String id) {
        return checks.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    /** Converts checks to test results that can be stored with the candidate run. */
    public List<TestResult> toTestResults() {
        return checks.stream().map(c -> TestResult.of("regression." + c.id(), switch (c.status()) {
            case PASS -> TestResult.Status.PASSED;
            case FAIL -> TestResult.Status.FAILED;
            case SKIP -> TestResult.Status.SKIPPED;
        }, c.message())).toList();
    }
}
