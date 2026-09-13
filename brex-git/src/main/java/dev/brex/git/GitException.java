package dev.brex.git;

/** A Git command failed, or Git is not installed. */
public class GitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GitException(String message) {
        super(message);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
