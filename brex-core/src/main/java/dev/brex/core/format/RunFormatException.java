package dev.brex.core.format;

/** Thrown when a run file cannot be read: corrupt, truncated, or from an incompatible version. */
public class RunFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RunFormatException(String message) {
        super(message);
    }

    public RunFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
