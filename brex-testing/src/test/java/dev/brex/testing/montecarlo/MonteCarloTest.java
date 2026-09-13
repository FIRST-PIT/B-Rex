package dev.brex.testing.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.trajectory.Trajectory;
import dev.brex.sim.ReferencePathSimulation;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonteCarloTest {

    static Run reference() {
        Trajectory.Builder path = new Trajectory.Builder();
        for (int i = 0; i <= 250; i++) {
            double t = i * 0.02;
            path.add(t, Pose.of(Math.min(1.0, t / 3), 0, 0));
        }
        return Run.builder("ref", RunMetadata.builder("blue-left").build())
                .duration(5)
                .trajectory(path.build())
                .mechanismState(MechanismStateChange.of(3, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(4, "lift", "IDLE"))
                .event(Event.of(4.5, "deposit.complete"))
                .build();
    }

    private final Run reference = reference();
    private final ReferencePathSimulation simulation = ReferencePathSimulation.of(reference);

    @Test
    void sameSeedGivesIdenticalResults() {
        MonteCarloConfig config = MonteCarloConfig.DEFAULTS.withRuns(50).withSeed(42);

        MonteCarloResult a = MonteCarlo.run(simulation, reference, config, SuccessCriteria.DEFAULTS);
        MonteCarloResult b = MonteCarlo.run(simulation, reference, config, SuccessCriteria.DEFAULTS);

        assertEquals(a.outcomes(), b.outcomes());
    }

    @Test
    void differentSeedsGiveDifferentResults() {
        MonteCarloConfig config = MonteCarloConfig.DEFAULTS.withRuns(20);

        MonteCarloResult a = MonteCarlo.run(simulation, reference, config.withSeed(1), SuccessCriteria.DEFAULTS);
        MonteCarloResult b = MonteCarlo.run(simulation, reference, config.withSeed(2), SuccessCriteria.DEFAULTS);

        assertNotEquals(a.outcomes().get(0).errorX(), b.outcomes().get(0).errorX());
    }

    @Test
    void eachRunIsIndependentOfRunCount() {
        MonteCarloConfig config = MonteCarloConfig.DEFAULTS.withSeed(7);

        MonteCarloResult small = MonteCarlo.run(simulation, reference, config.withRuns(5), SuccessCriteria.DEFAULTS);
        MonteCarloResult large = MonteCarlo.run(simulation, reference, config.withRuns(30), SuccessCriteria.DEFAULTS);

        assertEquals(small.outcomes(), large.outcomes().subList(0, 5));
        assertEquals(large.outcomes().get(3).parameters(), MonteCarlo.parametersFor(config, 3));
    }

    @Test
    void noVariationAlwaysSucceeds() {
        MonteCarloResult result = MonteCarlo.run(simulation, reference,
                MonteCarloConfig.DEFAULTS.withRuns(10).withoutVariation(), SuccessCriteria.DEFAULTS);

        assertEquals(10, result.successes());
        assertEquals(100.0, result.successRate());
        assertEquals(0, result.worstCase().positionError(), 0.01);
    }

    @Test
    void largePlacementErrorCausesFailuresWithReasons() {
        MonteCarloConfig sloppy = MonteCarloConfig.DEFAULTS.withRuns(200).withSeed(3)
                .withStart(Distribution.normal(0.04), Distribution.normal(0.04), Distribution.ZERO);

        MonteCarloResult result = MonteCarlo.run(simulation, reference, sloppy, SuccessCriteria.DEFAULTS);

        assertTrue(result.failures() > 20 && result.failures() < 180, "failures=" + result.failures());
        assertEquals("final position", result.failureReasons().keySet().iterator().next());
        MonteCarloOutcome worst = result.worstCase();
        assertTrue(worst.positionError() >= result.positionErrorPercentile(95));
        assertEquals(worst.parameters().startOffsetX(), worst.errorX(), 0.01, "placement error persists to the end");
    }

    @Test
    void slowMechanismsFailDurationLimit() {
        MonteCarloConfig slow = MonteCarloConfig.DEFAULTS.withRuns(10).withoutVariation()
                .withMechanismTiming(Distribution.constant(3), 0);

        MonteCarloResult result = MonteCarlo.run(simulation, reference, slow,
                new SuccessCriteria(0.05, Math.toRadians(5), 6, true, true));

        assertEquals(0, result.successes());
        assertTrue(result.outcomes().get(0).failures().contains("duration: 7.00s"),
                result.outcomes().get(0).failures().toString());
    }

    @Test
    void distributionsValidateAndSample() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(1);

        assertEquals(3.0, Distribution.constant(3).sample(random));
        double u = Distribution.uniform(-1, 1).sample(random);
        assertTrue(u >= -1 && u <= 1);
        assertThrows(IllegalArgumentException.class, () -> Distribution.uniform(1, -1));
        assertThrows(IllegalArgumentException.class, () -> Distribution.normal(-0.1));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloConfig.DEFAULTS.withRuns(0));
    }

    @Test
    void benchmarkSummarizesHistory() {
        List<Run> history = List.of(
                Run.builder("1", RunMetadata.builder("blue-left").build()).duration(22.0).build(),
                Run.builder("2", RunMetadata.builder("blue-left").build()).duration(23.0).build(),
                Run.builder("3", RunMetadata.builder("blue-left").build()).duration(30)
                        .status(dev.brex.core.run.RunStatus.INCOMPLETE).build());

        dev.brex.testing.benchmark.RunBenchmark benchmark = dev.brex.testing.benchmark.RunBenchmark.of("blue-left",
                history, 10);

        assertEquals(3, benchmark.count());
        assertEquals(200.0 / 3, benchmark.reliability(), 1e-9);
        assertEquals(22.5, benchmark.meanDuration(), 1e-9);
        assertEquals("1", benchmark.fastest().id());
        assertEquals(2, dev.brex.testing.benchmark.RunBenchmark.of("blue-left", history, 2).count());
    }
}
