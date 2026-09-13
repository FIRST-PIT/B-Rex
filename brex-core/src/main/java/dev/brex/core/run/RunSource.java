package dev.brex.core.run;

/** Where a run's data came from. Analysis never mixes these up silently. */
public enum RunSource {
    /** Recorded on a physical robot. */
    ROBOT,
    /** Produced by a B-rex simulation. */
    SIMULATION
}
