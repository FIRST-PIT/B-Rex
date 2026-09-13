package dev.brex.hardware.test;

/**
 * A check that exercises real (or fake) hardware, such as "the lift reaches the high basket in
 * under 1.2 seconds". Tests are loop-driven so they fit iterative OpModes: {@link #start} runs
 * once, {@link #update} runs every loop until the test finishes, and {@link #stop} always runs so
 * the test can leave mechanisms safe.
 */
public interface HardwareTest {

    String name();

    /** Seconds after which the test fails if it has not finished. */
    double timeoutSeconds();

    void start(HardwareTestContext context);

    /**
     * Called every loop. Call {@link HardwareTestContext#pass} or {@link HardwareTestContext#fail}
     * to finish.
     */
    void update(HardwareTestContext context);

    /** Always called once after the test finishes, fails or times out. Stop motors here. */
    void stop(HardwareTestContext context);

    /** The failure message when the test times out; override to include progress. */
    String timeoutMessage(HardwareTestContext context);
}
