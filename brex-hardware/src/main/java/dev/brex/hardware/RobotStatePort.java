package dev.brex.hardware;

/** Robot-wide state that is not tied to a single device. */
public interface RobotStatePort {

    /** Battery voltage in volts, or NaN when unknown. */
    double batteryVoltage();
}
