package dev.brex.sim;

import dev.brex.core.run.Run;

/** Produces a run from parameters. Implementations must be deterministic for a given seed. */
@FunctionalInterface
public interface Simulation {

    Run simulate(SimulationParameters parameters, long seed);
}
