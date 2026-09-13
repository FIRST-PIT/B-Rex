package dev.brex.cli.commands;

import dev.brex.cli.Arguments;
import dev.brex.cli.CliException;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.ExitCode;
import dev.brex.cli.OptionSpec;
import dev.brex.core.config.BrexConfig;
import dev.brex.testing.project.BrexProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code brex init}: creates brex.properties and the .brex directory. */
public final class InitCommand implements Command {

    static final String DEFAULT_CONFIG = """
            # B-rex project configuration. See docs/configuration.md for every key.
            #
            # Physical values must include their unit: s, ms, m, cm, mm, in, deg, rad.
            # Override any key for a single routine with: routine.<name>.<key> = <value>

            # Unit used to display distances: m, cm, mm or in
            units.distance = cm

            # ---- Regression thresholds (brex test) ---------------------------------
            regression.maxDurationIncrease = 0.5s
            regression.maxMeanPathDeviation = 3cm
            regression.maxPathDeviation = 10cm
            regression.maxFinalPositionError = 2.5cm
            regression.maxFinalHeadingError = 2deg
            regression.maxEventDelay = 0.5s
            regression.maxMechanismSlowdown = 0.25s
            regression.maxLocalizationErrorIncrease = 1cm
            regression.requireBaselineEvents = true

            # ---- Autonomous score (see docs/scoring.md) ----------------------------
            scoring.weight.speed = 1
            scoring.weight.accuracy = 1
            scoring.weight.consistency = 1
            scoring.weight.mechanisms = 1
            scoring.weight.reliability = 1
            # Duration that earns a full speed score; empty uses the baseline's duration
            scoring.targetDuration =
            scoring.timeLimit = 30s

            # ---- Mechanisms -----------------------------------------------------------
            # State names that mean "doing nothing"
            mechanisms.idleStates = IDLE, OFF, STOPPED, STOWED, HOME, REST
            """;

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String summary() {
        return "Create a B-rex project in the current directory";
    }

    @Override
    public String usage() {
        return "brex init [--force]";
    }

    @Override
    public String help() {
        return """
                Creates brex.properties and the .brex directory. Run it in the root of your
                robot code repository (next to TeamCode) so baselines are versioned with the code.
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(OptionSpec.flag("force", "Overwrite an existing brex.properties with defaults"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) throws IOException {
        arguments.requireAtMostPositionals(0);
        Path root = context.workingDirectory();
        Path config = root.resolve(BrexConfig.FILE_NAME);
        Path data = root.resolve(BrexProject.DATA_DIRECTORY);
        boolean existed = Files.exists(config);
        if (existed && !arguments.flag("force")) {
            context.info("B-rex project already initialized in " + root);
            context.info("Use --force to reset brex.properties to defaults.");
            return ExitCode.OK;
        }
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new CliException(root + " is not a directory");
        }
        Files.createDirectories(data.resolve("runs"));
        Files.createDirectories(data.resolve("baselines"));
        Files.writeString(config, DEFAULT_CONFIG);
        Path gitignore = data.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            Files.writeString(gitignore, "# Recorded runs are local; baselines are committed.\nruns/\n");
        }

        context.info(context.style().green("✓") + " Initialized B-rex project in " + root);
        context.info("  " + (existed ? "reset  " : "created") + " brex.properties");
        context.info("  created .brex/baselines/   (commit these)");
        context.info("  created .brex/runs/        (ignored by Git)");
        context.info("");
        context.info("Next steps:");
        context.info("  1. Record a run on the robot    RunRecorder brex = Brex.start(\"blue-left\");");
        context.info("  2. Copy runs to this computer   brex run pull");
        context.info("  3. Save a known-good baseline   brex baseline save blue-left");
        context.info("  4. Test new runs against it     brex test");
        return ExitCode.OK;
    }
}
