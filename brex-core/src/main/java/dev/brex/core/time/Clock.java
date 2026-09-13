package dev.brex.core.time;

/**
 * A monotonic time source in seconds.
 *
 * <p>Everything in B-rex that needs "now" takes a {@code Clock}, so recordings, hardware tests and
 * simulations can run deterministically against a {@link ManualClock}.
 */
public interface Clock {

    /** Seconds from an arbitrary but fixed origin. Must never decrease. */
    double seconds();
}
