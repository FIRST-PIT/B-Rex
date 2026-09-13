package dev.brex.hardware.test;

import dev.brex.core.run.TestResult;
import dev.brex.hardware.HardwareMonitor;
import dev.brex.hardware.RobotHardware;
import dev.brex.recording.RunRecorder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Runs hardware tests one after another, driven by the OpMode loop.
 *
 * <pre>{@code
 * // init()
 * brex = Brex.builder("pit-check").kind(RunKind.TEST).start();
 * runner = new HardwareTestRunner(hardware, brex, Arrays.asList(
 *         HardwareTests.motorTravels("lift", 1.0, 1200, 1.5),
 *         HardwareTests.sensorInRange("distance", "distanceCm", 5, 12, 0.5)));
 * // loop()
 * runner.update();
 * telemetry.addLine(runner.summary());
 * if (runner.isFinished()) brex.complete();
 * }</pre>
 *
 * <p>Every result is stored in the run, together with {@code hil.<test>.start} and
 * {@code hil.<test>.passed|failed} events and sampled hardware telemetry, so pit checks can be
 * reviewed later with {@code brex replay}.
 */
public final class HardwareTestRunner {

    private final RobotHardware hardware;
    private final RunRecorder recorder;
    private final HardwareMonitor monitor;
    private final List<HardwareTest> tests;
    private final List<TestResult> results = new ArrayList<>();
    private int index;
    private HardwareTestContext context;

    public HardwareTestRunner(RobotHardware hardware, RunRecorder recorder, List<HardwareTest> tests) {
        this.hardware = hardware;
        this.recorder = recorder;
        this.monitor = new HardwareMonitor(hardware, recorder);
        this.tests = new ArrayList<>(tests);
    }

    /** Advances the current test. Call once per loop. Never throws for test failures. */
    public void update() {
        if (isFinished()) {
            return;
        }
        monitor.sample();
        HardwareTest test = tests.get(index);
        if (context == null) {
            context = new HardwareTestContext(hardware, recorder);
            recorder.event("hil." + test.name() + ".start");
            try {
                test.start(context);
            } catch (RuntimeException e) {
                context.fail("threw " + describe(e) + " in start()");
            }
        }
        if (!context.finished()) {
            if (context.elapsed() > test.timeoutSeconds()) {
                String message;
                try {
                    message = test.timeoutMessage(context);
                } catch (RuntimeException e) {
                    message = "timed out";
                }
                context.fail(String.format(Locale.ROOT, "%s after %.2fs", message, test.timeoutSeconds()));
            } else {
                try {
                    test.update(context);
                } catch (RuntimeException e) {
                    context.fail("threw " + describe(e));
                }
            }
        }
        if (context.finished()) {
            try {
                test.stop(context);
            } catch (RuntimeException e) {
                recorder.warn("Hardware test " + test.name() + " threw " + describe(e) + " in stop()");
            }
            TestResult result = context.passed() ? TestResult.of(test.name(), TestResult.Status.PASSED,
                    context.message()) : TestResult.failed(test.name(), context.message());
            results.add(result);
            recorder.testResult(result);
            recorder.event("hil." + test.name() + (result.passed() ? ".passed" : ".failed"));
            index++;
            context = null;
        }
    }

    public boolean isFinished() {
        return index >= tests.size();
    }

    public List<TestResult> results() {
        return Collections.unmodifiableList(results);
    }

    public boolean allPassed() {
        for (TestResult result : results) {
            if (!result.passed()) {
                return false;
            }
        }
        return isFinished();
    }

    /** A short status line for Driver Station telemetry. */
    public String summary() {
        int passed = 0;
        for (TestResult result : results) {
            if (result.passed()) {
                passed++;
            }
        }
        String progress = passed + "/" + results.size() + " passed";
        if (isFinished()) {
            return "Hardware tests finished: " + progress;
        }
        return "Running " + tests.get(index).name() + " (" + (index + 1) + "/" + tests.size() + "), " + progress;
    }

    private static String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
