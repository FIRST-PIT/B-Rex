package dev.brex.replay;

import dev.brex.core.event.Event;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
import java.util.Map;

/**
 * The reconstructed state of the robot at one moment of a recorded run.
 *
 * @param time seconds since run start
 * @param pose interpolated pose, or null when no trajectory was recorded
 * @param velocity recorded or estimated velocity, or null when no trajectory was recorded
 * @param mechanismStates current state of every mechanism that has entered a state by now
 * @param numbers latest value of every numeric or boolean telemetry key sampled by now
 * @param texts latest value of every text telemetry key sampled by now
 * @param lastEvent the most recent event at or before {@code time}, or null
 * @param phase the phase containing {@code time}, or null
 */
public record RobotSnapshot(
        double time,
        Pose pose,
        Velocity velocity,
        Map<String, String> mechanismStates,
        Map<String, Double> numbers,
        Map<String, String> texts,
        Event lastEvent,
        String phase) {

    public RobotSnapshot {
        mechanismStates = Map.copyOf(mechanismStates);
        numbers = Map.copyOf(numbers);
        texts = Map.copyOf(texts);
    }
}
