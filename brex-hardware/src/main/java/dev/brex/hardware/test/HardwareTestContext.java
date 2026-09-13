package dev.brex.hardware.test;

import dev.brex.hardware.RobotHardware;
import dev.brex.recording.RunRecorder;

/** What a running {@link HardwareTest} can see and do. */
public final class HardwareTestContext {

    private final RobotHardware hardware;
    private final RunRecorder recorder;
    private final double startSeconds;
    private Boolean passed;
    private String message = "";

    HardwareTestContext(RobotHardware hardware, RunRecorder recorder) {
        this.hardware = hardware;
        this.recorder = recorder;
        this.startSeconds = hardware.clock().seconds();
    }

    public RobotHardware hardware() {
        return hardware;
    }

    public RunRecorder recorder() {
        return recorder;
    }

    /** Seconds since this test started. */
    public double elapsed() {
        return hardware.clock().seconds() - startSeconds;
    }

    public void pass(String message) {
        finish(true, message);
    }

    public void fail(String message) {
        finish(false, message);
    }

    boolean finished() {
        return passed != null;
    }

    boolean passed() {
        return Boolean.TRUE.equals(passed);
    }

    String message() {
        return message;
    }

    private void finish(boolean ok, String text) {
        if (passed == null) {
            passed = ok;
            message = text == null ? "" : text;
        }
    }
}
