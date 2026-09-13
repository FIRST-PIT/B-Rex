package dev.brex.core.time;

/** A clock that only moves when told to. Used by tests and simulations. */
public final class ManualClock implements Clock {

    private double now;

    public ManualClock() {
        this(0);
    }

    public ManualClock(double startSeconds) {
        this.now = startSeconds;
    }

    @Override
    public double seconds() {
        return now;
    }

    public void advance(double seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("A clock cannot move backwards (advance by " + seconds + "s)");
        }
        now += seconds;
    }

    public void set(double seconds) {
        if (seconds < now) {
            throw new IllegalArgumentException("A clock cannot move backwards (" + now + "s -> " + seconds + "s)");
        }
        now = seconds;
    }
}
