package dev.brex.core.run;

/** The outcome of one check performed against a run, stored with the run for later review. */
public final class TestResult {

    /** Outcome of a single check. */
    public enum Status {
        PASSED,
        FAILED,
        SKIPPED
    }

    private final String name;
    private final Status status;
    private final String message;

    private TestResult(String name, Status status, String message) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Test name must not be empty");
        }
        this.name = name;
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public static TestResult passed(String name) {
        return new TestResult(name, Status.PASSED, "");
    }

    public static TestResult failed(String name, String message) {
        return new TestResult(name, Status.FAILED, message);
    }

    public static TestResult skipped(String name, String reason) {
        return new TestResult(name, Status.SKIPPED, reason);
    }

    public static TestResult of(String name, Status status, String message) {
        return new TestResult(name, status, message);
    }

    public String name() {
        return name;
    }

    public Status status() {
        return status;
    }

    public boolean passed() {
        return status == Status.PASSED;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TestResult)) {
            return false;
        }
        TestResult other = (TestResult) o;
        return name.equals(other.name) && status == other.status && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + status.hashCode()) + message.hashCode();
    }

    @Override
    public String toString() {
        return "TestResult(" + name + ", " + status + (message.isEmpty() ? "" : ", " + message) + ")";
    }
}
