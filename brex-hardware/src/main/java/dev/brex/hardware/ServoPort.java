package dev.brex.hardware;

/** A servo. Standard FTC servos report only their commanded position. */
public interface ServoPort {

    String name();

    /** Commands a position in [0, 1]. */
    void setPosition(double position);

    /** The most recently commanded position. */
    double position();
}
