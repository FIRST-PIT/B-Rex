package dev.brex.testing;

/**
 * Thrown when an autonomous assertion fails. It extends {@link AssertionError}, so JUnit, TestNG
 * and plain {@code main} methods all report it as a test failure.
 */
public class BrexAssertionError extends AssertionError {

    private static final long serialVersionUID = 1L;

    public BrexAssertionError(String message) {
        super(message);
    }
}
