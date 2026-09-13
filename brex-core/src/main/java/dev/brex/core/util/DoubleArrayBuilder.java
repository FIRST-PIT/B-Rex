package dev.brex.core.util;

import java.util.Arrays;

/**
 * An append-only list of primitive doubles.
 *
 * <p>Recording happens inside robot loops, so B-rex avoids boxing samples into {@code Double}
 * objects that would pressure the garbage collector on the Control Hub.
 */
public final class DoubleArrayBuilder {

    private double[] values;
    private int size;

    public DoubleArrayBuilder() {
        this(64);
    }

    public DoubleArrayBuilder(int initialCapacity) {
        values = new double[Math.max(4, initialCapacity)];
    }

    public void add(double value) {
        if (size == values.length) {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = value;
    }

    public double get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        return values[index];
    }

    /** The last value added. The builder must not be empty. */
    public double last() {
        return get(size - 1);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public double[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
