package dev.brex.hardware;

import dev.brex.core.geometry.Pose;
import dev.brex.recording.RunRecorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the state of every registered device into a run, once per call to {@link #sample()}.
 *
 * <p>Telemetry keys follow the B-rex conventions ({@code motor.lift.position},
 * {@code servo.claw.position}, {@code sensor.distance.distanceCm}, {@code robot.batteryVoltage}),
 * and the localizer's pose goes into the run's trajectory. Keys are built once up front so
 * sampling does not allocate strings in the robot loop.
 */
public final class HardwareMonitor {

    private final RobotHardware hardware;
    private final RunRecorder recorder;
    private final List<MotorKeys> motors = new ArrayList<>();
    private final List<ServoKeys> servos = new ArrayList<>();
    private final List<EncoderKeys> encoders = new ArrayList<>();
    private final List<SensorKeys> sensors = new ArrayList<>();

    public HardwareMonitor(RobotHardware hardware, RunRecorder recorder) {
        this.hardware = hardware;
        this.recorder = recorder;
        for (MotorPort motor : hardware.motors()) {
            motors.add(new MotorKeys(motor));
        }
        for (ServoPort servo : hardware.servos()) {
            servos.add(new ServoKeys(servo));
        }
        for (EncoderPort encoder : hardware.encoders()) {
            encoders.add(new EncoderKeys(encoder));
        }
        for (SensorPort sensor : hardware.sensors()) {
            sensors.add(new SensorKeys(sensor));
        }
    }

    /** Reads every device and records the values. Device errors become run warnings. */
    public void sample() {
        for (MotorKeys m : motors) {
            try {
                recorder.telemetry.record(m.power, m.port.power());
                recorder.telemetry.record(m.position, m.port.position());
                recorder.telemetry.record(m.velocity, m.port.velocity());
                double current = m.port.current();
                if (!Double.isNaN(current)) {
                    recorder.telemetry.record(m.current, current);
                }
            } catch (RuntimeException e) {
                deviceError("motor " + m.port.name(), e);
            }
        }
        for (ServoKeys s : servos) {
            try {
                recorder.telemetry.record(s.position, s.port.position());
            } catch (RuntimeException e) {
                deviceError("servo " + s.port.name(), e);
            }
        }
        for (EncoderKeys e : encoders) {
            try {
                recorder.telemetry.record(e.position, e.port.position());
                recorder.telemetry.record(e.velocity, e.port.velocity());
            } catch (RuntimeException ex) {
                deviceError("encoder " + e.port.name(), ex);
            }
        }
        for (SensorKeys s : sensors) {
            try {
                for (int i = 0; i < s.fields.length; i++) {
                    recorder.telemetry.record(s.keys[i], s.port.read(s.fields[i]));
                }
            } catch (RuntimeException e) {
                deviceError("sensor " + s.port.name(), e);
            }
        }
        PoseEstimator estimator = hardware.poseEstimator();
        if (estimator != null) {
            try {
                Pose pose = estimator.pose();
                recorder.trajectory.record(pose, estimator.velocity());
            } catch (RuntimeException e) {
                deviceError("pose estimator", e);
            }
        }
        RobotStatePort state = hardware.robotState();
        if (state != null) {
            try {
                double voltage = state.batteryVoltage();
                if (!Double.isNaN(voltage)) {
                    recorder.telemetry.record("robot.batteryVoltage", voltage);
                }
            } catch (RuntimeException e) {
                deviceError("robot state", e);
            }
        }
    }

    private void deviceError(String device, RuntimeException e) {
        recorder.warn("Could not read " + device + ": " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " " + e.getMessage()));
    }

    private static final class MotorKeys {
        final MotorPort port;
        final String power;
        final String position;
        final String velocity;
        final String current;

        MotorKeys(MotorPort port) {
            this.port = port;
            String prefix = "motor." + port.name() + ".";
            power = prefix + "power";
            position = prefix + "position";
            velocity = prefix + "velocity";
            current = prefix + "current";
        }
    }

    private static final class ServoKeys {
        final ServoPort port;
        final String position;

        ServoKeys(ServoPort port) {
            this.port = port;
            position = "servo." + port.name() + ".position";
        }
    }

    private static final class EncoderKeys {
        final EncoderPort port;
        final String position;
        final String velocity;

        EncoderKeys(EncoderPort port) {
            this.port = port;
            position = "encoder." + port.name() + ".position";
            velocity = "encoder." + port.name() + ".velocity";
        }
    }

    private static final class SensorKeys {
        final SensorPort port;
        final String[] fields;
        final String[] keys;

        SensorKeys(SensorPort port) {
            this.port = port;
            this.fields = port.fields().clone();
            this.keys = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                keys[i] = "sensor." + port.name() + "." + fields[i];
            }
        }
    }
}
