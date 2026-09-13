package dev.brex.core.time;

/** Wall-clock time backed by {@link System#nanoTime()}. */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    @Override
    public double seconds() {
        return System.nanoTime() * 1e-9;
    }
}
