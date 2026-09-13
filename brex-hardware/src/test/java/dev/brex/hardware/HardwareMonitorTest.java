package dev.brex.hardware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.run.Run;
import dev.brex.core.time.ManualClock;
import dev.brex.hardware.fake.FakeMotor;
import dev.brex.hardware.fake.FakePoseEstimator;
import dev.brex.hardware.fake.FakeSensor;
import dev.brex.hardware.fake.FakeServo;
import dev.brex.recording.Brex;
import dev.brex.recording.RunRecorder;
import org.junit.jupiter.api.Test;

class HardwareMonitorTest {

    private final ManualClock clock = new ManualClock();

    @Test
    void recordsEveryDeviceUsingConventionalKeys() {
        FakeMotor lift = new FakeMotor("lift", clock, 1000);
        FakeServo claw = new FakeServo("claw");
        FakeSensor distance = new FakeSensor("distance", "distanceCm").set("distanceCm", 7.5);
        FakePoseEstimator odometry = new FakePoseEstimator().set(Pose.of(1, 2, 0), Velocity.of(0.5, 0, 0));
        RobotHardware hardware = RobotHardware.builder().clock(clock).motor(lift).servo(claw).sensor(distance)
                .poseEstimator(odometry).robotState(() -> 12.6).build();
        RunRecorder brex = Brex.builder("pit").clock(clock).start();
        HardwareMonitor monitor = new HardwareMonitor(hardware, brex);

        lift.setPower(0.5);
        claw.setPosition(0.8);
        monitor.sample();
        clock.advance(1);
        monitor.sample();
        Run run = brex.complete();

        assertEquals(500.0, run.telemetry().require("motor.lift.position").number(1), 1e-9);
        assertEquals(0.5, run.telemetry().require("motor.lift.power").number(0));
        assertEquals(1.0, run.telemetry().require("motor.lift.current").number(0));
        assertEquals(0.8, run.telemetry().require("servo.claw.position").number(0));
        assertEquals(7.5, run.telemetry().require("sensor.distance.distanceCm").number(0));
        assertEquals(12.6, run.telemetry().require("robot.batteryVoltage").number(0));
        assertEquals(2, run.trajectory().size());
        assertTrue(run.trajectory().hasRecordedVelocity());
    }

    @Test
    void deviceErrorsBecomeWarnings() {
        MotorPort broken = new MotorPort() {
            @Override
            public String name() {
                return "arm";
            }

            @Override
            public void setPower(double power) {
            }

            @Override
            public double power() {
                return 0;
            }

            @Override
            public double position() {
                throw new IllegalStateException("I2C timeout");
            }

            @Override
            public double velocity() {
                return 0;
            }

            @Override
            public double current() {
                return Double.NaN;
            }
        };
        RunRecorder brex = Brex.builder("pit").clock(clock).start();

        new HardwareMonitor(RobotHardware.builder().clock(clock).motor(broken).build(), brex).sample();
        Run run = brex.complete();

        assertEquals("Could not read motor arm: IllegalStateException I2C timeout", run.warnings().get(0).message());
        assertFalse(run.telemetry().has("motor.arm.current"));
    }

    @Test
    void unknownDeviceListsRegisteredNames() {
        RobotHardware hardware = RobotHardware.builder().motor(new FakeMotor("lift", clock, 1))
                .motor(new FakeMotor("intake", clock, 1)).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> hardware.motor("lfit"));

        assertEquals("No motor named 'lfit'; registered: [lift, intake]", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RobotHardware.builder()
                .servo(new FakeServo("claw")).servo(new FakeServo("claw")));
    }
}
