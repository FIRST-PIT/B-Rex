package dev.brex.hardware;

/** A standalone encoder, such as a dead-wheel odometry pod. */
public interface EncoderPort {

    String name();

    /** Position in ticks. */
    double position();

    /** Velocity in ticks per second. */
    double velocity();
}
