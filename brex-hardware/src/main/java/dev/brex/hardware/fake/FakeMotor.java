package dev.brex.hardware.fake;

import dev.brex.core.time.Clock;
import dev.brex.hardware.MotorPort;

/**
 * A motor whose encoder moves at {@code power × maxTicksPerSecond}, integrated against a clock.
 * Faults found in real robots can be injected: a reversed encoder, a stalled mechanism, or an
 * unplugged encoder.
 */
public final class FakeMotor implements MotorPort {

    private final String name;
    private final Clock clock;
    private final double maxTicksPerSecond;
    private double power;
    private double position;
    private double lastUpdate;
    private double encoderDirection = 1;
    private boolean stalled;
    private boolean encoderUnplugged;
    private double currentPerPower = 2.0;

    public FakeMotor(String name, Clock clock, double maxTicksPerSecond) {
        this.name = name;
        this.clock = clock;
        this.maxTicksPerSecond = maxTicksPerSecond;
        this.lastUpdate = clock.seconds();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setPower(double power) {
        integrate();
        this.power = Math.max(-1, Math.min(1, power));
    }

    @Override
    public double power() {
        return power;
    }

    @Override
    public double position() {
        integrate();
        return encoderUnplugged ? 0 : position * encoderDirection;
    }

    @Override
    public double velocity() {
        integrate();
        return encoderUnplugged || stalled ? 0 : power * maxTicksPerSecond * encoderDirection;
    }

    @Override
    public double current() {
        return Math.abs(power) * (stalled ? currentPerPower * 4 : currentPerPower);
    }

    /** Simulates an encoder wired or configured in the wrong direction. */
    public FakeMotor reverseEncoder() {
        encoderDirection = -1;
        return this;
    }

    /** Simulates a jammed mechanism: power is applied but nothing moves. */
    public FakeMotor stall() {
        integrate();
        stalled = true;
        return this;
    }

    /** Simulates an unplugged encoder cable: position stays at zero. */
    public FakeMotor unplugEncoder() {
        encoderUnplugged = true;
        return this;
    }

    private void integrate() {
        double now = clock.seconds();
        if (!stalled) {
            position += power * maxTicksPerSecond * (now - lastUpdate);
        }
        lastUpdate = now;
    }
}
