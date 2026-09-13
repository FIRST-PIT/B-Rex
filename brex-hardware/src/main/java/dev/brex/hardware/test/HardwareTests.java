package dev.brex.hardware.test;

import dev.brex.hardware.MotorPort;
import dev.brex.hardware.RobotStatePort;
import dev.brex.hardware.SensorPort;
import java.util.Locale;

/** Ready-made hardware tests for common pit checks. */
public final class HardwareTests {

    private HardwareTests() {
    }

    /**
     * Drives a motor at {@code power} until its encoder has travelled {@code ticks} in the
     * direction of the power. Detects stalls, reversed encoders and unplugged encoder cables.
     */
    public static HardwareTest motorTravels(final String motor, final double power, final double ticks,
            final double timeoutSeconds) {
        return new HardwareTest() {
            private MotorPort port;
            private double start;

            @Override
            public String name() {
                return motor + ".travel";
            }

            @Override
            public double timeoutSeconds() {
                return timeoutSeconds;
            }

            @Override
            public void start(HardwareTestContext context) {
                port = context.hardware().motor(motor);
                start = port.position();
                port.setPower(power);
            }

            @Override
            public void update(HardwareTestContext context) {
                double travelled = (port.position() - start) * Math.signum(power);
                if (travelled >= ticks) {
                    context.pass(format("%s travelled %.0f ticks in %.2fs", motor, travelled, context.elapsed()));
                } else if (travelled <= -0.1 * ticks) {
                    context.fail(format("%s moved %.0f ticks against power %+.2f: the motor or encoder direction "
                            + "is reversed", motor, travelled, power));
                }
            }

            @Override
            public void stop(HardwareTestContext context) {
                if (port != null) {
                    port.setPower(0);
                }
            }

            @Override
            public String timeoutMessage(HardwareTestContext context) {
                double travelled = (port.position() - start) * Math.signum(power);
                if (travelled == 0) {
                    return motor + " encoder never changed: check the encoder cable, or the mechanism is jammed";
                }
                return format("%s only travelled %.0f of %.0f ticks", motor, travelled, ticks);
            }
        };
    }

    /** Waits until a sensor reading is within {@code [min, max]}. */
    public static HardwareTest sensorInRange(final String sensor, final String field, final double min,
            final double max, final double timeoutSeconds) {
        return new HardwareTest() {
            private SensorPort port;
            private double last = Double.NaN;

            @Override
            public String name() {
                return sensor + "." + field;
            }

            @Override
            public double timeoutSeconds() {
                return timeoutSeconds;
            }

            @Override
            public void start(HardwareTestContext context) {
                port = context.hardware().sensor(sensor);
            }

            @Override
            public void update(HardwareTestContext context) {
                last = port.read(field);
                if (last >= min && last <= max) {
                    context.pass(format("%s.%s read %.2f (expected %.2f to %.2f)", sensor, field, last, min, max));
                }
            }

            @Override
            public void stop(HardwareTestContext context) {
            }

            @Override
            public String timeoutMessage(HardwareTestContext context) {
                return format("%s.%s read %.2f, expected %.2f to %.2f", sensor, field, last, min, max);
            }
        };
    }

    /** Checks that the battery voltage is at least {@code volts}. */
    public static HardwareTest batteryAbove(final double volts) {
        return new HardwareTest() {
            @Override
            public String name() {
                return "battery";
            }

            @Override
            public double timeoutSeconds() {
                return 1;
            }

            @Override
            public void start(HardwareTestContext context) {
            }

            @Override
            public void update(HardwareTestContext context) {
                RobotStatePort state = context.hardware().robotState();
                if (state == null || Double.isNaN(state.batteryVoltage())) {
                    context.fail("battery voltage is not available");
                } else if (state.batteryVoltage() >= volts) {
                    context.pass(format("battery at %.2fV", state.batteryVoltage()));
                } else {
                    context.fail(format("battery at %.2fV, need at least %.2fV: charge or swap it",
                            state.batteryVoltage(), volts));
                }
            }

            @Override
            public void stop(HardwareTestContext context) {
            }

            @Override
            public String timeoutMessage(HardwareTestContext context) {
                return "battery check did not finish";
            }
        };
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
