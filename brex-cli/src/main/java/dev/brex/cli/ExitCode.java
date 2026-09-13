package dev.brex.cli;

/** Process exit codes. CI systems rely on these, so they are part of the CLI contract. */
public final class ExitCode {

    /** Success; for {@code brex test}, every test passed. */
    public static final int OK = 0;
    /** Tests ran and at least one failed or regressed. */
    public static final int FAILED = 1;
    /** The command line was invalid. */
    public static final int USAGE = 2;
    /** B-rex could not do its job: missing files, unreadable runs, I/O errors. */
    public static final int ERROR = 3;

    private ExitCode() {
    }
}
