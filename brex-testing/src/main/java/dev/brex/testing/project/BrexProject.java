package dev.brex.testing.project;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.regression.RegressionThresholds;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.config.BrexConfig;
import dev.brex.core.config.ConfigException;
import dev.brex.core.run.Run;
import dev.brex.recording.RunStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * A B-rex project on a development machine: {@code brex.properties} plus the {@code .brex}
 * directory holding recorded runs and baselines.
 *
 * <pre>
 * my-robot/
 * ├── brex.properties        configuration (commit this)
 * └── .brex/
 *     ├── runs/              recorded runs (ignored by Git)
 *     └── baselines/         known-good runs (commit these)
 * </pre>
 */
public final class BrexProject {

    public static final String DATA_DIRECTORY = ".brex";

    private final Path root;
    private final BrexConfig config;
    private final RunStore runs;
    private final BaselineStore baselines;

    private BrexProject(Path root, BrexConfig config) {
        this.root = root;
        this.config = config;
        this.runs = new RunStore(root.resolve(config.string("project.runsDir", DATA_DIRECTORY + "/runs")).toFile());
        this.baselines = new BaselineStore(root.resolve(config.string("project.baselinesDir",
                DATA_DIRECTORY + "/baselines")));
    }

    /** Opens the project containing the current directory, or the current directory itself. */
    public static BrexProject open() {
        return open(Path.of("").toAbsolutePath());
    }

    /** Opens the nearest project at or above {@code start}; falls back to {@code start}. */
    public static BrexProject open(Path start) {
        return at(findRoot(start).orElse(start.toAbsolutePath()));
    }

    /** Opens the project rooted exactly at {@code root}. */
    public static BrexProject at(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        try {
            return new BrexProject(absolute, BrexConfig.load(absolute.resolve(BrexConfig.FILE_NAME).toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + absolute.resolve(BrexConfig.FILE_NAME), e);
        }
    }

    /** The nearest directory at or above {@code start} with a {@code brex.properties} or {@code .brex}. */
    public static Optional<Path> findRoot(Path start) {
        for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(BrexConfig.FILE_NAME)) || Files.isDirectory(dir.resolve(DATA_DIRECTORY))) {
                return Optional.of(dir);
            }
        }
        return Optional.empty();
    }

    public Path root() {
        return root;
    }

    public boolean isInitialized() {
        return Files.isRegularFile(root.resolve(BrexConfig.FILE_NAME)) || Files.isDirectory(root.resolve(DATA_DIRECTORY));
    }

    public BrexConfig config() {
        return config;
    }

    public RunStore runs() {
        return runs;
    }

    public BaselineStore baselines() {
        return baselines;
    }

    /** The most recent run of a routine. */
    public Run latestRun(String routine) {
        Run run = runs.latest(routine);
        if (run == null) {
            throw new IllegalStateException("No runs of '" + routine + "' in " + root.relativize(runs.directory()
                    .toPath().toAbsolutePath()) + ". Record one on the robot and copy it with 'brex run pull', or "
                    + "import a file with 'brex run import <file>'.");
        }
        return run;
    }

    /** Runs of a routine, oldest first. */
    public List<Run> history(String routine) {
        return runs.runsOf(routine);
    }

    public Optional<Run> baseline(String routine) {
        try {
            return baselines.load(routine);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Regression thresholds for a routine, from {@code regression.*} keys. */
    public RegressionThresholds regressionThresholds(String routine) {
        BrexConfig c = config.forRoutine(routine);
        RegressionThresholds d = RegressionThresholds.DEFAULTS;
        try {
            return new RegressionThresholds(
                    c.seconds("regression.maxDurationIncrease", d.maxDurationIncrease()),
                    c.meters("regression.maxMeanPathDeviation", d.maxMeanPathDeviation()),
                    c.meters("regression.maxPathDeviation", d.maxPathDeviation()),
                    c.meters("regression.maxFinalPositionError", d.maxFinalPositionError()),
                    c.radians("regression.maxFinalHeadingError", d.maxFinalHeadingError()),
                    c.seconds("regression.maxEventDelay", d.maxEventDelay()),
                    c.seconds("regression.maxMechanismSlowdown", d.maxMechanismSlowdown()),
                    c.meters("regression.maxLocalizationErrorIncrease", d.maxLocalizationErrorIncrease()),
                    c.bool("regression.requireBaselineEvents", d.requireBaselineEvents()));
        } catch (IllegalArgumentException e) {
            throw new ConfigException(config.source() + ": " + e.getMessage(), e);
        }
    }

    /** Scoring configuration for a routine, from {@code scoring.*} keys. */
    public ScoringConfig scoringConfig(String routine) {
        BrexConfig c = config.forRoutine(routine);
        ScoringConfig d = ScoringConfig.DEFAULTS;
        try {
            return new ScoringConfig(
                    c.number("scoring.weight.speed", d.speedWeight()),
                    c.number("scoring.weight.accuracy", d.accuracyWeight()),
                    c.number("scoring.weight.consistency", d.consistencyWeight()),
                    c.number("scoring.weight.mechanisms", d.mechanismsWeight()),
                    c.number("scoring.weight.reliability", d.reliabilityWeight()),
                    c.seconds("scoring.targetDuration", d.targetSeconds()),
                    c.seconds("scoring.timeLimit", d.timeLimitSeconds()),
                    c.integer("scoring.historyWindow", d.historyWindow()),
                    c.integer("scoring.minConsistencyRuns", d.minConsistencyRuns()),
                    d.position(), d.heading(), d.durationSpread(), d.timing());
        } catch (IllegalArgumentException e) {
            throw new ConfigException(config.source() + ": " + e.getMessage(), e);
        }
    }

    /** Idle mechanism state names, from {@code mechanisms.idleStates}. */
    public IdleStates idleStates() {
        List<String> names = config.list("mechanisms.idleStates", null);
        return names == null ? IdleStates.DEFAULT : new IdleStates(new HashSet<>(names));
    }
}
