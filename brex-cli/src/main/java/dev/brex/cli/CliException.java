package dev.brex.cli;

/**
 * A user-facing failure. The message is printed as-is, so it should say what went wrong and,
 * where possible, what to do about it.
 */
public class CliException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int exitCode;

    public CliException(String message) {
        this(message, ExitCode.ERROR);
    }

    public CliException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public CliException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = ExitCode.ERROR;
    }

    public int exitCode() {
        return exitCode;
    }

    /** An invalid command line. */
    public static CliException usage(String message) {
        return new CliException(message, ExitCode.USAGE);
    }
}
