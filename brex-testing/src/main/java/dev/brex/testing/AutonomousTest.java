package dev.brex.testing;

import dev.brex.analysis.regression.RegressionThresholds;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for autonomous unit tests. Supply the run under test; every assertion then reads
 * like a sentence about your routine.
 *
 * <pre>{@code
 * class BlueLeftTest extends AutonomousTest {
 *     @Override
 *     protected Run run() {
 *         return BrexProject.open().latestRun("blue-left");
 *     }
 *
 *     @Test
 *     void scoresSix() {
 *         assertScores(6);
 *     }
 *
 *     @Test
 *     void finishesUnder25Seconds() {
 *         assertTimeBelow(25.0);
 *     }
 *
 *     @Test
 *     void depositsAfterAligning() {
 *         assertEventBefore("alignment.complete", "deposit.start");
 *         assertElapsedBetween("deposit.start", "deposit.complete", 1.5);
 *     }
 * }
 * }</pre>
 *
 * <p>The class has no dependency on a test framework: failures are {@link AssertionError}s. Units
 * are meters, seconds and degrees.
 */
public abstract class AutonomousTest {

    private Run cachedRun;

    /** The run under test. Called once per test instance. */
    protected abstract Run run();

    /** The known-good run for comparisons, or null. Override to enable baseline assertions. */
    protected Run baseline() {
        return null;
    }

    /** Earlier runs of the routine, oldest first, used for reliability and consistency. */
    protected List<Run> history() {
        return List.of();
    }

    protected ScoringConfig scoringConfig() {
        return ScoringConfig.DEFAULTS;
    }

    protected RegressionThresholds regressionThresholds() {
        return RegressionThresholds.DEFAULTS;
    }

    /** The run under test, loaded on first use. */
    protected final Run currentRun() {
        if (cachedRun == null) {
            cachedRun = run();
            if (cachedRun == null) {
                throw new BrexAssertionError(getClass().getSimpleName() + ".run() returned null");
            }
        }
        return cachedRun;
    }

    protected final void assertCompleted() {
        RunAssertions.assertCompleted(currentRun());
    }

    protected final void assertTimeBelow(double seconds) {
        RunAssertions.assertTimeBelow(currentRun(), seconds);
    }

    protected final void assertDurationBetween(double minSeconds, double maxSeconds) {
        RunAssertions.assertDurationBetween(currentRun(), minSeconds, maxSeconds);
    }

    protected final void assertNoErrors() {
        RunAssertions.assertNoErrors(currentRun());
    }

    protected final void assertNoWarnings() {
        RunAssertions.assertNoWarnings(currentRun());
    }

    protected final void assertEndPositionNear(Pose expected, double toleranceMeters) {
        RunAssertions.assertEndPositionNear(currentRun(), expected, toleranceMeters);
    }

    protected final void assertEndHeadingNear(double expectedDegrees, double toleranceDegrees) {
        RunAssertions.assertEndHeadingNear(currentRun(), expectedDegrees, toleranceDegrees);
    }

    protected final void assertEndPoseNear(Pose expected, double toleranceMeters, double toleranceDegrees) {
        RunAssertions.assertEndPoseNear(currentRun(), expected, toleranceMeters, toleranceDegrees);
    }

    protected final void assertPositionAt(double time, Pose expected, double toleranceMeters) {
        RunAssertions.assertPositionAt(currentRun(), time, expected, toleranceMeters);
    }

    protected final void assertDistanceBelow(double meters) {
        RunAssertions.assertDistanceBelow(currentRun(), meters);
    }

    protected final void assertLocalizationErrorBelow(double meters) {
        RunAssertions.assertLocalizationErrorBelow(currentRun(), meters);
    }

    protected final void assertPathDeviationBelow(double meters) {
        RunAssertions.assertPathDeviationBelow(currentRun(), requireBaseline("assertPathDeviationBelow"), meters);
    }

    protected final void assertEventOccurred(String event) {
        RunAssertions.assertEventOccurred(currentRun(), event);
    }

    protected final void assertEventNotOccurred(String event) {
        RunAssertions.assertEventNotOccurred(currentRun(), event);
    }

    protected final void assertEventCount(String event, int expected) {
        RunAssertions.assertEventCount(currentRun(), event, expected);
    }

    protected final void assertEventBefore(String first, String second) {
        RunAssertions.assertEventBefore(currentRun(), first, second);
    }

    protected final void assertEventOrder(String... events) {
        RunAssertions.assertEventOrder(currentRun(), events);
    }

    protected final void assertEventWithin(String event, double seconds) {
        RunAssertions.assertEventWithin(currentRun(), event, seconds);
    }

    protected final void assertElapsedBetween(String fromEvent, String toEvent, double seconds) {
        RunAssertions.assertElapsedBetween(currentRun(), fromEvent, toEvent, seconds);
    }

    protected final void assertMechanismStateAt(String mechanism, String state, double time) {
        RunAssertions.assertMechanismStateAt(currentRun(), mechanism, state, time);
    }

    protected final void assertMechanismReached(String mechanism, String state) {
        RunAssertions.assertMechanismReached(currentRun(), mechanism, state);
    }

    protected final void assertFinalMechanismState(String mechanism, String state) {
        RunAssertions.assertFinalMechanismState(currentRun(), mechanism, state);
    }

    protected final void assertStateDurationBelow(String mechanism, String state, double seconds) {
        RunAssertions.assertStateDurationBelow(currentRun(), mechanism, state, seconds);
    }

    protected final void assertTelemetryBelow(String key, double max) {
        RunAssertions.assertTelemetryBelow(currentRun(), key, max);
    }

    protected final void assertTelemetryAbove(String key, double min) {
        RunAssertions.assertTelemetryAbove(currentRun(), key, min);
    }

    protected final void assertTelemetryReached(String key, double value) {
        RunAssertions.assertTelemetryReached(currentRun(), key, value);
    }

    protected final void assertTelemetryAt(String key, double time, double expected, double tolerance) {
        RunAssertions.assertTelemetryAt(currentRun(), key, time, expected, tolerance);
    }

    protected final void assertFinalTelemetry(String key, double expected, double tolerance) {
        RunAssertions.assertFinalTelemetry(currentRun(), key, expected, tolerance);
    }

    protected final void assertScores(double points) {
        RunAssertions.assertScores(currentRun(), points);
    }

    protected final void assertScoresAtLeast(double points) {
        RunAssertions.assertScoresAtLeast(currentRun(), points);
    }

    protected final void assertAutonScoreAtLeast(double minimum) {
        RunAssertions.assertAutonScoreAtLeast(currentRun(), baseline(), history(), scoringConfig(), minimum);
    }

    /** Reliability over {@link #history()} plus the run under test. */
    protected final void assertReliabilityAtLeast(double percent) {
        List<Run> runs = new ArrayList<>(history());
        runs.add(currentRun());
        RunAssertions.assertReliabilityAtLeast(runs, percent);
    }

    protected final void assertNoRegression() {
        RunAssertions.assertNoRegression(currentRun(), requireBaseline("assertNoRegression"),
                regressionThresholds());
    }

    private Run requireBaseline(String assertion) {
        Run baseline = baseline();
        if (baseline == null) {
            throw new BrexAssertionError(assertion + " needs a baseline: override baseline() in "
                    + getClass().getSimpleName());
        }
        return baseline;
    }
}
