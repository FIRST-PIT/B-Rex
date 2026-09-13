package dev.brex.hardware;

/**
 * A motor with an attached encoder. On the robot, wrap a {@code DcMotorEx}; in tests, use
 * {@code FakeMotor}.
 */
public interface MotorPort {

    String name();

    /** Commands a power in [-1, 1]. */
    void setPower(double power);

    /** The most recently commanded power. */
    double power();

    /** Encoder position in ticks. */
    double position();

    /** Encoder velocity in ticks per second. */
    double velocity();

    /** Current draw in amps, or NaN when the hardware cannot measure it. */
    double current();
}
