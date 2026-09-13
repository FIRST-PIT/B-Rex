/**
 * Simulation: producing runs without a robot.
 *
 * <p>A {@link dev.brex.sim.Simulation} turns {@link dev.brex.sim.SimulationParameters} and a seed
 * into a {@link dev.brex.core.run.Run} with {@code RunSource.SIMULATION}. Everything downstream
 * (assertions, regression checks, scoring, replay, Monte Carlo) treats simulated runs exactly
 * like recorded ones.
 *
 * <p>The first implementation, {@link dev.brex.sim.ReferencePathSimulation}, re-drives a recorded
 * run's path with a kinematic holonomic follower. It answers questions such as "how far off does
 * this routine end if the robot is placed 2 cm wrong?" or "what does 1% odometry scale error do
 * to the final pose?". It is deliberately not a physics engine: there is no wheel slip, motor
 * saturation, collision or game-piece model. Higher-fidelity models plug in behind the same
 * {@code Simulation} interface.
 */
package dev.brex.sim;
