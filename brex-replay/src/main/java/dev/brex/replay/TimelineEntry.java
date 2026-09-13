package dev.brex.replay;

/**
 * One discrete thing that happened during a run.
 *
 * @param time seconds since run start
 * @param kind what happened
 * @param subject the event name, mechanism, phase or log level
 * @param detail the new state, log message or event attributes; empty when not applicable
 * @param previous the mechanism's previous state for {@link Kind#MECHANISM_STATE}, else null
 */
public record TimelineEntry(double time, Kind kind, String subject, String detail, String previous) {

    /** Categories of timeline entries. */
    public enum Kind {
        PHASE_START,
        PHASE_END,
        EVENT,
        MECHANISM_STATE,
        LOG
    }
}
