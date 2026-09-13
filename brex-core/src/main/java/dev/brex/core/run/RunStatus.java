package dev.brex.core.run;

/** How a run ended. */
public enum RunStatus {
    /** The routine signalled that it finished everything it set out to do. */
    COMPLETED,
    /** Recording stopped before the routine signalled completion (for example, time ran out). */
    INCOMPLETE,
    /** The routine failed with an error. */
    FAILED
}
