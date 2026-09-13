package dev.brex.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunSource;
import dev.brex.core.run.RunStatus;
import dev.brex.core.trajectory.Trajectory;
import org.junit.jupiter.api.Test;

class ReferencePathSimulationTest {

    /**
     * Drive 1.5 m forward in 3 s, wait 2 s while the lift raises, strafe 0.5 m in 1.5 s, then hold
     * still for 1 s so the final pose is settled.
     */
    static Run reference() {
        Trajectory.Builder path = new Trajectory.Builder();
        for (int i = 0; i <= 375; i++) {
            double t = i * 0.02;
            double x = t < 3 ? 0.5 * t : 1.5;
            double y = t < 5 ? 0 : Math.min(0.5, (t - 5) / 3);
            path.add(t, Pose.of(x, y, 0));
        }
        return Run.builder("ref", RunMetadata.builder("blue-left").gitCommit("a91f42c").build())
                .duration(7.5)
                .trajectory(path.build())
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .mechanismState(MechanismStateChange.of(3, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(5, "lift", "IDLE"))
                .event(Event.of(3, "lift.start"))
                .event(Event.of(5, "lift.done"))
                .event(Event.of(6.5, "park"))
                .build();
    }

    private final ReferencePathSimulation simulation = ReferencePathSimulation.of(reference());

    @Test
    void nominalParametersReproduceTheReference() {
        Run run = simulation.simulate(SimulationParameters.NOMINAL, 1);

        assertEquals(RunSource.SIMULATION, run.metadata().source());
        assertEquals("a91f42c", run.metadata().gitCommit());
        assertEquals(7.5, run.duration(), 1e-9);
        assertEquals(0, run.endPose().distanceTo(reference().endPose()), 0.01);
        assertEquals(6.5, run.firstEvent("park").time(), 1e-9);
        assertTrue(run.telemetry().has("localization.error"));
    }

    @Test
    void placementErrorPersistsBecauseLocalizerTrustsNominalStart() {
        Run run = simulation.simulate(SimulationParameters.NOMINAL.withStartOffset(0.02, -0.01, 0), 1);

        Pose end = run.endPose();
        Pose expected = reference().endPose();
        assertEquals(0.02, end.x() - expected.x(), 0.005);
        assertEquals(-0.01, end.y() - expected.y(), 0.005);
        assertEquals(Math.hypot(0.02, 0.01), run.telemetry().require("localization.error").numberAt(99), 0.005);
    }

    @Test
    void odometryScaleErrorMakesRobotOvershoot() {
        Run run = simulation.simulate(SimulationParameters.NOMINAL.withOdometryScaleError(0.02), 1);

        assertTrue(run.endPose().x() > reference().endPose().x() + 0.02, run.endPose().toString());
    }

    @Test
    void slowerMechanismsDelayEverythingAfterThem() {
        Run run = simulation.simulate(SimulationParameters.NOMINAL.withMechanismTiming(1.5, 0), 1);

        assertEquals(8.5, run.duration(), 1e-9);
        assertEquals(3.0, run.firstEvent("lift.start").time(), 1e-9);
        assertEquals(6.0, run.firstEvent("lift.done").time(), 1e-9);
        assertEquals(0, run.endPose().distanceTo(reference().endPose()), 0.01);
    }

    @Test
    void exceedingTimeLimitTruncatesRun() {
        ReferencePathSimulation tight = new ReferencePathSimulation(reference(), FollowerModel.DEFAULT,
                dev.brex.analysis.IdleStates.DEFAULT, 7.0, 0.02);

        Run run = tight.simulate(SimulationParameters.NOMINAL.withMechanismTiming(1.5, 0), 1);

        assertEquals(RunStatus.INCOMPLETE, run.status());
        assertEquals(7.0, run.duration(), 1e-9);
        assertFalse(run.hasEvent("park"));
    }

    @Test
    void sameSeedIsBitForBitReproducible() {
        SimulationParameters noisy = SimulationParameters.NOMINAL.withNoise(0.01, 0.01).withMechanismTiming(1, 0.3);

        Run a = simulation.simulate(noisy, 42);
        Run b = simulation.simulate(noisy, 42);
        Run c = simulation.simulate(noisy, 43);

        assertArrayEquals(a.trajectory().xs(), b.trajectory().xs());
        assertEquals(a.duration(), b.duration());
        assertEquals("sim-blue-left-2a", a.id());
        assertFalse(a.duration() == c.duration() && a.endPose().equals(c.endPose()));
    }

    @Test
    void requiresRecordedTrajectory() {
        Run noPath = Run.builder("x", RunMetadata.builder("blue-left").build()).duration(5).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ReferencePathSimulation.of(noPath));

        assertTrue(error.getMessage().contains("has no recorded trajectory"));
    }

    @Test
    void timeWarpIsMonotoneAndInvertible() {
        TimeWarp warp = TimeWarp.stretching(new double[] {1, 4}, new double[] {2, 6}, new double[] {1, -5});

        assertEquals(0.5, warp.toSimulated(0.5), 1e-12);
        assertEquals(3.0, warp.toSimulated(2), 1e-12);
        assertEquals(5.0, warp.toSimulated(4), 1e-12);
        assertEquals(5.0, warp.toSimulated(6), 1e-12, "a state cannot shrink below zero length");
        assertEquals(8.0, warp.toSimulated(9), 1e-12);
        assertEquals(1.5, warp.toReference(2.0), 1e-12);
        assertEquals(9.0, warp.toReference(8.0), 1e-12);
    }
}
