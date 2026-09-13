package dev.brex.sim;

import dev.brex.analysis.IdleStates;
import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Angles;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunSource;
import dev.brex.core.run.RunStatus;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import dev.brex.core.util.DoubleArrayBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Re-executes a recorded run's path under different conditions.
 *
 * <h2>Model</h2>
 *
 * <ol>
 *   <li>The robot is physically placed at the reference start pose plus the start offset, but its
 *       localizer is initialized at the intended start pose, as happens when a team places the
 *       robot by hand.</li>
 *   <li>Every {@code dt}, the follower steers the <em>estimated</em> pose toward the reference
 *       pose (see {@link FollowerModel}). The true pose integrates the commanded velocity.</li>
 *   <li>The estimate integrates the true displacement scaled by {@code 1 - odometryScaleError};
 *       the controller sees it with Gaussian sensor noise added.</li>
 *   <li>Active (non-idle) mechanism states are stretched by the timing scale and jitter. The whole
 *       reference timeline, including the path, is warped accordingly, so the robot waits longer
 *       where mechanisms are slower.</li>
 *   <li>A run that exceeds the time limit is cut off and marked incomplete; events after the limit
 *       never happen.</li>
 * </ol>
 *
 * <p>The result records the true trajectory, the reference events and mechanism states at their
 * warped times, and {@code localization.error} (distance between estimate and truth).
 */
public final class ReferencePathSimulation implements Simulation {

    private final Run reference;
    private final FollowerModel follower;
    private final IdleStates idleStates;
    private final double timeLimit;
    private final double dt;

    public ReferencePathSimulation(Run reference, FollowerModel follower, IdleStates idleStates, double timeLimit,
            double dt) {
        if (reference.trajectory().size() < 2) {
            throw new IllegalArgumentException("Run " + reference.id() + " has no recorded trajectory to simulate "
                    + "(record poses with brex.trajectory)");
        }
        if (!(dt > 0) || !(timeLimit > 0)) {
            throw new IllegalArgumentException("dt and timeLimit must be positive");
        }
        this.reference = reference;
        this.follower = follower;
        this.idleStates = idleStates;
        this.timeLimit = timeLimit;
        this.dt = dt;
    }

    /** A simulation with the default follower, idle states, a 30 s limit and a 50 Hz loop. */
    public static ReferencePathSimulation of(Run reference) {
        return new ReferencePathSimulation(reference, FollowerModel.DEFAULT, IdleStates.DEFAULT, 30.0, 0.02);
    }

    public Run reference() {
        return reference;
    }

    @Override
    public Run simulate(SimulationParameters p, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        TimeWarp warp = timeWarp(p, random);
        Trajectory path = reference.trajectory();
        double simDuration = warp.toSimulated(reference.duration());
        double end = Math.min(simDuration, timeLimit);

        Pose nominalStart = path.startPose();
        double tx = nominalStart.x() + p.startOffsetX();
        double ty = nominalStart.y() + p.startOffsetY();
        double th = nominalStart.heading() + p.startOffsetHeading();
        double ex = nominalStart.x();
        double ey = nominalStart.y();
        double eh = nominalStart.heading();
        double vx = 0;
        double vy = 0;

        Trajectory.Builder truth = new Trajectory.Builder();
        DoubleArrayBuilder errorTimes = new DoubleArrayBuilder();
        DoubleArrayBuilder errors = new DoubleArrayBuilder();
        int steps = (int) Math.floor(end / dt + 1e-9);
        double previousReference = 0;
        for (int step = 0; step <= steps; step++) {
            double t = step == steps ? end : step * dt;
            double h = step == 0 ? 0 : t - (step - 1) * dt;
            double tau = Math.min(warp.toReference(t), reference.duration());

            if (step > 0) {
                Pose target = path.poseAt(tau);
                Velocity feedForward = path.velocityAt(tau);
                double referenceRate = h > 0 ? (tau - previousReference) / h : 1;
                double mx = ex + gaussian(random, p.sensorNoise());
                double my = ey + gaussian(random, p.sensorNoise());
                double mh = eh + gaussian(random, p.headingNoise());

                double cmdX = feedForward.vx() * referenceRate + follower.positionGain() * (target.x() - mx);
                double cmdY = feedForward.vy() * referenceRate + follower.positionGain() * (target.y() - my);
                double speed = Math.hypot(cmdX, cmdY);
                if (speed > follower.maxSpeed()) {
                    cmdX *= follower.maxSpeed() / speed;
                    cmdY *= follower.maxSpeed() / speed;
                }
                double dvx = cmdX - vx;
                double dvy = cmdY - vy;
                double dv = Math.hypot(dvx, dvy);
                double maxDv = follower.maxAcceleration() * h;
                if (dv > maxDv) {
                    dvx *= maxDv / dv;
                    dvy *= maxDv / dv;
                }
                vx += dvx;
                vy += dvy;
                double omega = feedForward.omega() * referenceRate
                        + follower.headingGain() * Angles.difference(mh, target.heading());
                omega = Math.max(-follower.maxTurnRate(), Math.min(follower.maxTurnRate(), omega));

                double moveX = vx * h;
                double moveY = vy * h;
                tx += moveX;
                ty += moveY;
                th += omega * h;
                double measured = 1 - p.odometryScaleError();
                ex += moveX * measured;
                ey += moveY * measured;
                eh += omega * h;
            }
            previousReference = tau;
            truth.add(t, tx, ty, th, vx, vy, Double.NaN);
            errorTimes.add(t);
            errors.add(Math.hypot(ex - tx, ey - ty));
        }

        Run.Builder run = Run.builder(runId(seed), metadata(p, seed))
                .status(simDuration > timeLimit ? RunStatus.INCOMPLETE : reference.status())
                .duration(end)
                .startPose(nominalStart)
                .trajectory(truth.build())
                .telemetry(new Telemetry(List.of(TelemetryChannel.numeric("localization.error", errorTimes.toArray(),
                        errors.toArray()))));
        for (Event event : reference.events()) {
            double t = warp.toSimulated(event.time());
            if (t <= end) {
                run.event(Event.of(t, event.name(), event.attributes()));
            }
        }
        for (MechanismStateChange change : reference.mechanismStates()) {
            double t = warp.toSimulated(change.time());
            if (t <= end) {
                run.mechanismState(MechanismStateChange.of(t, change.mechanism(), change.state()));
            }
        }
        return run.build();
    }

    /** Stretches every completed, active mechanism state of the reference. */
    TimeWarp timeWarp(SimulationParameters p, SplittableRandom random) {
        if (p.mechanismTimingScale() == 1 && p.mechanismTimingJitter() == 0) {
            return TimeWarp.identity();
        }
        List<double[]> intervals = new ArrayList<>();
        for (String mechanism : reference.mechanismNames()) {
            List<MechanismStateChange> changes = reference.mechanismStates(mechanism);
            for (int i = 0; i + 1 < changes.size(); i++) {
                if (!idleStates.isIdle(changes.get(i).state())) {
                    intervals.add(new double[] {changes.get(i).time(), changes.get(i + 1).time()});
                }
            }
        }
        intervals.sort((a, b) -> Double.compare(a[0], b[0]));
        // Merge overlapping intervals (two mechanisms working at once) so the warp stays monotone.
        List<double[]> merged = new ArrayList<>();
        for (double[] interval : intervals) {
            if (!merged.isEmpty() && interval[0] <= merged.get(merged.size() - 1)[1]) {
                double[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], interval[1]);
            } else {
                merged.add(interval.clone());
            }
        }
        double[] starts = new double[merged.size()];
        double[] ends = new double[merged.size()];
        double[] extra = new double[merged.size()];
        for (int i = 0; i < merged.size(); i++) {
            starts[i] = merged.get(i)[0];
            ends[i] = merged.get(i)[1];
            extra[i] = (p.mechanismTimingScale() - 1) * (ends[i] - starts[i]) + gaussian(random, p.mechanismTimingJitter());
        }
        return TimeWarp.stretching(starts, ends, extra);
    }

    private RunMetadata metadata(SimulationParameters p, long seed) {
        return reference.metadata().toBuilder()
                .source(RunSource.SIMULATION)
                .property("sim.reference", reference.id())
                .property("sim.seed", Long.toString(seed))
                .property("sim.startOffset", String.format(Locale.ROOT, "%.4f,%.4f,%.5f", p.startOffsetX(),
                        p.startOffsetY(), p.startOffsetHeading()))
                .property("sim.sensorNoise", String.format(Locale.ROOT, "%.4f,%.5f", p.sensorNoise(), p.headingNoise()))
                .property("sim.odometryScaleError", Double.toString(p.odometryScaleError()))
                .property("sim.mechanismTiming", String.format(Locale.ROOT, "%.3f,%.3f", p.mechanismTimingScale(),
                        p.mechanismTimingJitter()))
                .build();
    }

    private String runId(long seed) {
        return "sim-" + dev.brex.core.run.RunIds.slug(reference.name()) + "-" + Long.toHexString(seed);
    }

    private static double gaussian(SplittableRandom random, double sigma) {
        return sigma == 0 ? 0 : random.nextGaussian() * sigma;
    }
}
