package dev.brex.cli;

import dev.brex.core.event.Event;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.format.RunWriter;
import dev.brex.core.geometry.Pose;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunStatus;
import dev.brex.core.trajectory.Trajectory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Realistic runs for CLI tests. */
final class CliFixtures {

    private CliFixtures() {
    }

    /** A blue-left run lasting {@code duration}s whose path is shifted by {@code yOffset} meters. */
    static Run blueLeft(String id, long startedAt, double duration, double yOffset, RunStatus status) {
        double d = duration;
        Trajectory.Builder trajectory = new Trajectory.Builder();
        for (int i = 0; i <= 100; i++) {
            double f = i / 100.0;
            trajectory.add(f * d, Pose.of(2 * f, yOffset + f, Math.toRadians(90 * f)));
        }
        return Run.builder(id, RunMetadata.builder("blue-left").startedAtEpochMillis(startedAt).gitCommit("a91f42c9e1")
                        .robot("Rex").build())
                .status(status)
                .duration(d)
                .trajectory(trajectory.build())
                .event(Event.of(0.2 * d, "alignment.complete"))
                .event(Event.of(0.6 * d, "deposit.start"))
                .event(Event.of(0.7 * d, "deposit.complete", Map.of("points", "4")))
                .mechanismState(MechanismStateChange.of(0, "lift", "IDLE"))
                .mechanismState(MechanismStateChange.of(0.5 * d, "lift", "RAISING"))
                .mechanismState(MechanismStateChange.of(0.55 * d, "lift", "HIGH"))
                .build();
    }

    static Path write(Path directory, Run run) throws IOException {
        Path file = directory.resolve(run.id() + ".run.json");
        RunWriter.write(run, file.toFile());
        return file;
    }
}
