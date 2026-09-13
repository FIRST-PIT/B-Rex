/**
 * Recorded-state replay: stepping through what a robot did, as it was recorded.
 *
 * <p>B-rex distinguishes three kinds of replay, and this package only implements the first:
 *
 * <ol>
 *   <li><b>Recorded-state replay</b> (here): reads a stored run and reconstructs the robot's
 *       pose, mechanism states, telemetry and events at any moment. Nothing is executed.</li>
 *   <li><b>Simulation replay</b> ({@code dev.brex.sim}): re-executes a routine against a
 *       simulated robot, possibly with different parameters, producing a new run.</li>
 *   <li><b>Hardware replay</b>: re-sending recorded motor commands to a physical robot. This is
 *       intentionally not implemented: recorded commands are only meaningful in the exact
 *       conditions they were recorded in, and blindly replaying them can damage mechanisms.</li>
 * </ol>
 */
package dev.brex.replay;
