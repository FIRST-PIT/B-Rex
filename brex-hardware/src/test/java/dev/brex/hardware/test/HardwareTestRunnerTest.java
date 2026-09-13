package dev.brex.hardware.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.run.Run;
import dev.brex.core.run.TestResult;
import dev.brex.core.time.ManualClock;
import dev.brex.hardware.RobotHardware;
import dev.brex.hardware.fake.FakeMotor;
import dev.brex.hardware.fake.FakeSensor;
import dev.brex.recording.Brex;
import dev.brex.recording.RunRecorder;
import java.util.List;
import org.junit.jupiter.api.Test;

class HardwareTestRunnerTest {

    private final ManualClock clock = new ManualClock();

    private Run runToCompletion(RobotHardware hardware, List<HardwareTest> tests) {
        RunRecorder brex = Brex.builder("pit-check").clock(clock).start();
        HardwareTestRunner runner = new HardwareTestRunner(hardware, brex, tests);
        int loops = 0;
        while (!runner.isFinished() && loops++ < 10_000) {
            runner.update();
            clock.advance(0.02);
        }
        return brex.complete();
    }

    private RobotHardware hardwareWith(FakeMotor lift) {
        FakeSensor distance = new FakeSensor("distance", "distanceCm").set("distanceCm", 30);
        return RobotHardware.builder().clock(clock).motor(lift).sensor(distance).robotState(() -> 12.9).build();
    }

    @Test
    void passingMotorTestReportsTravelTimeAndStopsMotor() {
        FakeMotor lift = new FakeMotor("lift", clock, 1000);

        Run run = runToCompletion(hardwareWith(lift), List.of(HardwareTests.motorTravels("lift", 1.0, 1200, 2)));

        TestResult result = run.testResults().get(0);
        assertTrue(result.passed(), result.message());
        assertEquals("lift travelled 1200 ticks in 1.20s", result.message());
        assertEquals(0.0, lift.power());
        assertTrue(run.hasEvent("hil.lift.travel.start"));
        assertTrue(run.hasEvent("hil.lift.travel.passed"));
        assertTrue(run.telemetry().has("motor.lift.position"));
    }

    @Test
    void detectsReversedEncoder() {
        FakeMotor lift = new FakeMotor("lift", clock, 1000).reverseEncoder();

        TestResult result = runToCompletion(hardwareWith(lift), List.of(HardwareTests.motorTravels("lift", 1.0, 1200,
                2))).testResults().get(0);

        assertFalse(result.passed());
        assertEquals("lift moved -120 ticks against power +1.00: the motor or encoder direction is reversed",
                result.message());
    }

    @Test
    void detectsUnpluggedEncoderOnTimeout() {
        FakeMotor lift = new FakeMotor("lift", clock, 1000).unplugEncoder();

        TestResult result = runToCompletion(hardwareWith(lift), List.of(HardwareTests.motorTravels("lift", 1.0, 1200,
                1.5))).testResults().get(0);

        assertEquals("lift encoder never changed: check the encoder cable, or the mechanism is jammed after 1.50s",
                result.message());
    }

    @Test
    void sensorAndBatteryChecks() {
        Run run = runToCompletion(hardwareWith(new FakeMotor("lift", clock, 1)), List.of(
                HardwareTests.sensorInRange("distance", "distanceCm", 5, 12, 0.5),
                HardwareTests.batteryAbove(12.5)));

        assertEquals("distance.distanceCm read 30.00, expected 5.00 to 12.00 after 0.50s",
                run.testResults().get(0).message());
        assertEquals("battery at 12.90V", run.testResults().get(1).message());
    }

    @Test
    void exceptionsInTestsFailTheTestAndRunnerContinues() {
        HardwareTest throwing = new HardwareTest() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public double timeoutSeconds() {
                return 1;
            }

            @Override
            public void start(HardwareTestContext context) {
                context.hardware().motor("missing");
            }

            @Override
            public void update(HardwareTestContext context) {
            }

            @Override
            public void stop(HardwareTestContext context) {
            }

            @Override
            public String timeoutMessage(HardwareTestContext context) {
                return "timed out";
            }
        };
        RunRecorder brex = Brex.builder("pit-check").clock(clock).start();
        HardwareTestRunner runner = new HardwareTestRunner(hardwareWith(new FakeMotor("lift", clock, 1)), brex,
                List.of(throwing, HardwareTests.batteryAbove(12)));

        runner.update();
        runner.update();

        assertTrue(runner.isFinished());
        assertFalse(runner.allPassed());
        assertTrue(runner.results().get(0).message().startsWith("threw IllegalArgumentException: No motor named "
                + "'missing'"), runner.results().get(0).message());
        assertEquals("Hardware tests finished: 1/2 passed", runner.summary());
    }
}
