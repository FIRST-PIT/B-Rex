package dev.brex.testing.montecarlo;

import dev.brex.core.geometry.Angles;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunStatus;
import dev.brex.sim.Simulation;
import dev.brex.sim.SimulationParameters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Randomized robustness testing: run a simulation many times under sampled conditions and count
 * how often the routine still succeeds.
 *
 * <p>Results are reproducible. Run {@code i} derives its own seed from the master seed and
 * {@code i} alone, so the same seed gives identical results regardless of run count, thread count
 * or execution order.
 */
public final class MonteCarlo {

    private MonteCarlo() {
    }

    public static MonteCarloResult run(Simulation simulation, Run reference, MonteCarloConfig config,
            SuccessCriteria criteria) {
        Pose referenceEnd = reference.endPose();
        if (referenceEnd == null) {
            throw new IllegalArgumentException("Reference run " + reference.id() + " has no end pose");
        }
        List<String> referenceEvents = reference.eventNames();
        List<MonteCarloOutcome> outcomes = IntStream.range(0, config.runs()).parallel()
                .mapToObj(i -> simulateOne(simulation, referenceEnd, referenceEvents, config, criteria, i))
                .toList();
        return new MonteCarloResult(reference.name(), reference.id(), config, criteria, outcomes);
    }

    /** The parameters and simulation seed of run {@code index}, for reproducing a single run. */
    public static SimulationParameters parametersFor(MonteCarloConfig config, int index) {
        return sample(config, new SplittableRandom(runSeed(config.seed(), index)));
    }

    /** SplitMix64 finalizer: decorrelates neighboring seeds. */
    static long runSeed(long seed, int index) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static MonteCarloOutcome simulateOne(Simulation simulation, Pose referenceEnd, List<String> referenceEvents,
            MonteCarloConfig config, SuccessCriteria criteria, int index) {
        SplittableRandom random = new SplittableRandom(runSeed(config.seed(), index));
        SimulationParameters parameters = sample(config, random);
        long simulationSeed = random.nextLong();
        Run run = simulation.simulate(parameters, simulationSeed);

        Pose end = run.endPose();
        double errorX = end == null ? Double.NaN : end.x() - referenceEnd.x();
        double errorY = end == null ? Double.NaN : end.y() - referenceEnd.y();
        double headingError = end == null ? Double.NaN : Angles.difference(referenceEnd.heading(), end.heading());

        List<String> failures = new ArrayList<>();
        if (criteria.requireCompletion() && run.status() != RunStatus.COMPLETED) {
            failures.add("incomplete: run " + run.status().name().toLowerCase(Locale.ROOT));
        }
        if (end == null || Math.hypot(errorX, errorY) > criteria.maxFinalPositionError()) {
            failures.add(String.format(Locale.ROOT, "final position: %.1fcm from reference",
                    Math.hypot(errorX, errorY) * 100));
        }
        if (end == null || Math.abs(headingError) > criteria.maxFinalHeadingError()) {
            failures.add(String.format(Locale.ROOT, "final heading: %.1f° from reference",
                    Math.toDegrees(Math.abs(headingError))));
        }
        if (run.duration() > criteria.maxDuration()) {
            failures.add(String.format(Locale.ROOT, "duration: %.2fs", run.duration()));
        }
        if (criteria.requireEvents()) {
            for (String event : referenceEvents) {
                if (!run.hasEvent(event)) {
                    failures.add("missing event: " + event);
                }
            }
        }
        return new MonteCarloOutcome(index, simulationSeed, parameters, failures, errorX, errorY, headingError,
                run.duration());
    }

    private static SimulationParameters sample(MonteCarloConfig config, SplittableRandom random) {
        double startX = config.startX().sample(random);
        double startY = config.startY().sample(random);
        double startHeading = config.startHeading().sample(random);
        double scaleError = config.odometryScaleError().sample(random);
        double timingScale = Math.max(0.05, config.mechanismTimingScale().sample(random));
        Map<String, Double> environment = new HashMap<>();
        config.environment().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> environment.put(e.getKey(), e.getValue().sample(random)));
        return new SimulationParameters(startX, startY, startHeading, config.sensorNoise(), config.headingNoise(),
                scaleError, timingScale, config.mechanismTimingJitter(), environment);
    }
}
