package dev.brex.analysis.compare;

import dev.brex.core.geometry.Pose;

/**
 * One candidate trajectory sample compared against the baseline ("ghost") run.
 *
 * <p>Two alignments are reported because they answer different questions:
 *
 * <ul>
 *   <li><b>Time-aligned</b>: where was the ghost at the same moment? Use it to render both robots
 *       side by side.</li>
 *   <li><b>Path-aligned</b>: where is the closest matching point on the ghost's path? Use it to
 *       judge accuracy independently of speed, and to see where time was gained or lost.</li>
 * </ul>
 *
 * @param time candidate time, seconds since start
 * @param candidate candidate pose
 * @param ghost baseline pose at the same time (clamped to the baseline's recorded range)
 * @param positionError time-aligned distance between candidate and ghost, meters
 * @param headingError time-aligned signed heading difference (candidate minus ghost), radians
 * @param matched closest matching point on the baseline path
 * @param pathDeviation distance from the candidate to the baseline path, meters
 * @param pathHeadingError signed heading difference against the matched point, radians
 * @param matchedBaselineTime baseline time at the matched point, seconds
 * @param timingOffset {@code time - matchedBaselineTime}: positive means the candidate reached
 *     this part of the path later than the baseline did
 * @param speedDifference candidate speed minus baseline speed at the matched point, m/s
 */
public record GhostSample(
        double time,
        Pose candidate,
        Pose ghost,
        double positionError,
        double headingError,
        Pose matched,
        double pathDeviation,
        double pathHeadingError,
        double matchedBaselineTime,
        double timingOffset,
        double speedDifference) {
}
