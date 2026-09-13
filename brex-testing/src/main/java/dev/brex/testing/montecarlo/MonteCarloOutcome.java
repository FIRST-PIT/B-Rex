package dev.brex.testing.montecarlo;

import dev.brex.sim.SimulationParameters;
import java.util.List;

/**
 * The result of one simulated run. Simulated runs are not kept in memory; re-create any of them
 * from {@code seed} and {@code parameters}.
 *
 * @param index zero-based run number
 * @param seed simulation seed
 * @param parameters the sampled conditions
 * @param failures reasons the run failed; empty on success
 * @param errorX final x minus reference final x (m)
 * @param errorY final y minus reference final y (m)
 * @param headingError final heading minus reference final heading (rad)
 * @param duration simulated duration (s)
 */
public record MonteCarloOutcome(int index, long seed, SimulationParameters parameters, List<String> failures,
        double errorX, double errorY, double headingError, double duration) {

    public MonteCarloOutcome {
        failures = List.copyOf(failures);
    }

    public boolean success() {
        return failures.isEmpty();
    }

    public double positionError() {
        return Math.hypot(errorX, errorY);
    }
}
