package dev.brex.cli;

import dev.brex.core.format.RunReader;
import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.testing.project.BrexProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared lookups and output used by several commands. */
public final class CommandSupport {

    private CommandSupport() {
    }

    /** The project containing the working directory; fails if none is initialized. */
    public static BrexProject requireProject(CommandContext context) {
        BrexProject project = BrexProject.open(context.workingDirectory());
        if (!project.isInitialized()) {
            throw new CliException("Not a B-rex project: no brex.properties or .brex directory in "
                    + context.workingDirectory() + " or any parent. Run 'brex init' first.");
        }
        warnUnknownKeys(context, project);
        return project;
    }

    /** The project containing the working directory, initialized or not. */
    public static BrexProject openProject(CommandContext context) {
        BrexProject project = BrexProject.open(context.workingDirectory());
        if (project.isInitialized()) {
            warnUnknownKeys(context, project);
        }
        return project;
    }

    /**
     * Resolves a run reference: a path to a run file, or anything {@code RunStore.resolve}
     * accepts (latest, #41, a routine name, an ID fragment).
     */
    public static Run resolveRun(CommandContext context, BrexProject project, String reference) {
        Path path = context.workingDirectory().resolve(reference);
        if (Files.isRegularFile(path)) {
            try {
                return RunReader.read(path.toFile());
            } catch (IOException e) {
                throw new CliException("Could not read " + path + ": " + e.getMessage());
            } catch (RuntimeException e) {
                throw new CliException(e.getMessage());
            }
        }
        try {
            return project.runs().resolve(reference);
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage());
        }
    }

    /** The display unit for distances from {@code units.distance}. */
    public static DistanceUnit distanceUnit(BrexProject project) {
        try {
            return project.config().distanceUnit();
        } catch (RuntimeException e) {
            throw new CliException(e.getMessage());
        }
    }

    public static Map<String, Object> runJson(Run run) {
        return JsonReports.run(run);
    }

    /** "RUN #42" when the run is in the project's store, otherwise "RUN". */
    public static String runLabel(BrexProject project, Run run) {
        int ordinal = project.runs().ordinal(run);
        return ordinal > 0 ? "RUN #" + ordinal : "RUN";
    }

    /** A multi-line description of a run. */
    public static void printRunSummary(CommandContext context, BrexProject project, Run run) {
        Style style = context.style();
        DistanceUnit unit = distanceUnit(project);
        RunMetadata m = run.metadata();
        context.out().println(style.bold(runLabel(project, run) + "  " + run.name()) + "  " + style.dim(run.id()));
        row(context, "Status", Fmt.status(run) + " in " + Fmt.seconds(run.duration()));
        row(context, "Started", Fmt.date(m.startedAtEpochMillis()));
        List<String> source = new ArrayList<>();
        source.add(m.source().name().toLowerCase(java.util.Locale.ROOT));
        if (m.robot() != null) {
            source.add("robot " + m.robot());
        }
        if (m.softwareVersion() != null) {
            source.add("software " + m.softwareVersion());
        }
        row(context, "Source", String.join(" · ", source));
        if (m.gitCommit() != null) {
            String git = Fmt.shortCommit(m.gitCommit());
            if (m.gitBranch() != null) {
                git += " on " + m.gitBranch();
            }
            if (Boolean.TRUE.equals(m.gitDirty())) {
                git += " (uncommitted changes)";
            }
            row(context, "Commit", git);
        }
        row(context, "Start pose", Fmt.pose(run.startPose(), unit));
        row(context, "End pose", Fmt.pose(run.endPose(), unit));
        row(context, "Recorded", run.trajectory().size() + " poses · " + run.telemetry().size() + " telemetry keys · "
                + run.events().size() + " events · " + run.mechanismNames().size() + " mechanisms");
        row(context, "Log", run.errors().size() + " errors · " + run.warnings().size() + " warnings");
        if (run.pointsScored() != 0) {
            row(context, "Points", Fmt.quantity(run.pointsScored(), dev.brex.analysis.Unit.COUNT, unit));
        }
        if (!run.phases().isEmpty()) {
            row(context, "Phases", String.join(" · ", run.phases().stream()
                    .map(p -> p.name() + " " + Fmt.seconds(p.duration())).toList()));
        }
        if (!m.robotConfig().isEmpty()) {
            row(context, "Config", m.robotConfig().toString());
        }
    }

    private static void row(CommandContext context, String label, String value) {
        context.out().println("  " + Fmt.pad(label, 11) + value);
    }

    private static void warnUnknownKeys(CommandContext context, BrexProject project) {
        for (String key : project.config().unknownKeys()) {
            context.err().println(context.style().yellow("warning: ") + "unknown key '" + key + "' in "
                    + project.config().source());
        }
    }
}
