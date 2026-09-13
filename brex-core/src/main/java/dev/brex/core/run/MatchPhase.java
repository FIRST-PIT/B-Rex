package dev.brex.core.run;

import dev.brex.core.util.Names;

/** A named interval of a run, such as {@code autonomous} or {@code teleop} in a match. */
public final class MatchPhase {

    public static final String AUTONOMOUS = "autonomous";
    public static final String TRANSITION = "transition";
    public static final String TELEOP = "teleop";
    public static final String ENDGAME = "endgame";

    private final String name;
    private final double start;
    private final double end;

    private MatchPhase(String name, double start, double end) {
        this.name = Names.requireValid("Phase name", name);
        if (end < start) {
            throw new IllegalArgumentException("Phase '" + name + "' ends (" + end + "s) before it starts (" + start
                    + "s)");
        }
        this.start = start;
        this.end = end;
    }

    public static MatchPhase of(String name, double start, double end) {
        return new MatchPhase(name, start, end);
    }

    public String name() {
        return name;
    }

    public double start() {
        return start;
    }

    public double end() {
        return end;
    }

    public double duration() {
        return end - start;
    }

    public boolean contains(double time) {
        return time >= start && time <= end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchPhase)) {
            return false;
        }
        MatchPhase other = (MatchPhase) o;
        return name.equals(other.name) && Double.compare(start, other.start) == 0
                && Double.compare(end, other.end) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + Double.hashCode(start)) + Double.hashCode(end);
    }

    @Override
    public String toString() {
        return "MatchPhase(" + name + ", " + start + "s-" + end + "s)";
    }
}
