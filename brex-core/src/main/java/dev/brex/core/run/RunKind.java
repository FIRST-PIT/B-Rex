package dev.brex.core.run;

/** What a run represents. */
public enum RunKind {
    /** A single autonomous routine. */
    AUTONOMOUS,
    /** A driver-controlled period recorded on its own. */
    TELEOP,
    /** A full match: autonomous, transition and tele-op, separated by {@link MatchPhase}s. */
    MATCH,
    /** A hardware-in-the-loop or mechanism test run. */
    TEST
}
