package dev.brex.hardware.fake;

import dev.brex.hardware.ServoPort;

/** A servo that remembers its commanded position. */
public final class FakeServo implements ServoPort {

    private final String name;
    private double position;

    public FakeServo(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setPosition(double position) {
        this.position = Math.max(0, Math.min(1, position));
    }

    @Override
    public double position() {
        return position;
    }
}
