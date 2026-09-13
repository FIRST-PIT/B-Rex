package dev.brex.hardware;

import dev.brex.core.time.Clock;
import dev.brex.core.time.SystemClock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The devices B-rex may observe and, in hardware tests, command. Nothing here depends on the FTC
 * SDK: adapt your {@code HardwareMap} devices to the port interfaces once, and the same tests run
 * against fakes on a laptop.
 */
public final class RobotHardware {

    private final Map<String, MotorPort> motors;
    private final Map<String, ServoPort> servos;
    private final Map<String, EncoderPort> encoders;
    private final Map<String, SensorPort> sensors;
    private final PoseEstimator poseEstimator;
    private final RobotStatePort robotState;
    private final Clock clock;

    private RobotHardware(Builder b) {
        this.motors = Collections.unmodifiableMap(new LinkedHashMap<>(b.motors));
        this.servos = Collections.unmodifiableMap(new LinkedHashMap<>(b.servos));
        this.encoders = Collections.unmodifiableMap(new LinkedHashMap<>(b.encoders));
        this.sensors = Collections.unmodifiableMap(new LinkedHashMap<>(b.sensors));
        this.poseEstimator = b.poseEstimator;
        this.robotState = b.robotState;
        this.clock = b.clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MotorPort motor(String name) {
        return find("motor", motors, name);
    }

    public ServoPort servo(String name) {
        return find("servo", servos, name);
    }

    public EncoderPort encoder(String name) {
        return find("encoder", encoders, name);
    }

    public SensorPort sensor(String name) {
        return find("sensor", sensors, name);
    }

    public Collection<MotorPort> motors() {
        return motors.values();
    }

    public Collection<ServoPort> servos() {
        return servos.values();
    }

    public Collection<EncoderPort> encoders() {
        return encoders.values();
    }

    public Collection<SensorPort> sensors() {
        return sensors.values();
    }

    /** The localizer, or null. */
    public PoseEstimator poseEstimator() {
        return poseEstimator;
    }

    /** Robot-wide state, or null. */
    public RobotStatePort robotState() {
        return robotState;
    }

    public Clock clock() {
        return clock;
    }

    private static <T> T find(String kind, Map<String, T> devices, String name) {
        T device = devices.get(name);
        if (device == null) {
            throw new IllegalArgumentException("No " + kind + " named '" + name + "'"
                    + (devices.isEmpty() ? " (no " + kind + "s registered)" : "; registered: " + devices.keySet()));
        }
        return device;
    }

    /** Builder for {@link RobotHardware}. */
    public static final class Builder {
        private final Map<String, MotorPort> motors = new LinkedHashMap<>();
        private final Map<String, ServoPort> servos = new LinkedHashMap<>();
        private final Map<String, EncoderPort> encoders = new LinkedHashMap<>();
        private final Map<String, SensorPort> sensors = new LinkedHashMap<>();
        private PoseEstimator poseEstimator;
        private RobotStatePort robotState;
        private Clock clock = SystemClock.INSTANCE;

        private Builder() {
        }

        public Builder motor(MotorPort motor) {
            put("motor", motors, motor.name(), motor);
            return this;
        }

        public Builder servo(ServoPort servo) {
            put("servo", servos, servo.name(), servo);
            return this;
        }

        public Builder encoder(EncoderPort encoder) {
            put("encoder", encoders, encoder.name(), encoder);
            return this;
        }

        public Builder sensor(SensorPort sensor) {
            put("sensor", sensors, sensor.name(), sensor);
            return this;
        }

        public Builder poseEstimator(PoseEstimator estimator) {
            this.poseEstimator = estimator;
            return this;
        }

        public Builder robotState(RobotStatePort state) {
            this.robotState = state;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public RobotHardware build() {
            return new RobotHardware(this);
        }

        private static <T> void put(String kind, Map<String, T> devices, String name, T device) {
            if (devices.containsKey(name)) {
                throw new IllegalArgumentException("A " + kind + " named '" + name + "' is already registered");
            }
            devices.put(name, device);
        }
    }
}
