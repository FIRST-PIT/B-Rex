package dev.brex.core.event;

/** A timestamped message recorded during a run, such as a warning about a stalled motor. */
public final class LogEntry {

    private final double time;
    private final LogLevel level;
    private final String message;

    private LogEntry(double time, LogLevel level, String message) {
        if (level == null) {
            throw new IllegalArgumentException("Log level must not be null");
        }
        this.time = time;
        this.level = level;
        this.message = message == null ? "" : message;
    }

    public static LogEntry of(double time, LogLevel level, String message) {
        return new LogEntry(time, level, message);
    }

    public double time() {
        return time;
    }

    public LogLevel level() {
        return level;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LogEntry)) {
            return false;
        }
        LogEntry other = (LogEntry) o;
        return Double.compare(time, other.time) == 0 && level == other.level && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Double.hashCode(time) + level.hashCode()) + message.hashCode();
    }

    @Override
    public String toString() {
        return "LogEntry(" + time + "s, " + level + ", " + message + ")";
    }
}
