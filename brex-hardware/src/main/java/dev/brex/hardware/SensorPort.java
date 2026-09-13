package dev.brex.hardware;

/**
 * A sensor with one or more numeric readings, e.g. a distance sensor ({@code distanceCm}) or a
 * color sensor ({@code red}, {@code green}, {@code blue}).
 */
public interface SensorPort {

    String name();

    /** The reading names this sensor provides. Must not change after construction. */
    String[] fields();

    /** The current value of one reading. */
    double read(String field);
}
