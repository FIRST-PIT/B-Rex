package dev.brex.cli.commands;

import dev.brex.cli.Arguments;
import dev.brex.cli.CliException;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.CommandSupport;
import dev.brex.cli.ExitCode;
import dev.brex.cli.Fmt;
import dev.brex.cli.JsonReports;
import dev.brex.cli.OptionSpec;
import dev.brex.cli.Style;
import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.replay.ReplaySession;
import dev.brex.replay.RobotSnapshot;
import dev.brex.replay.TimelineEntry;
import dev.brex.replay.TimelineEntry.Kind;
import dev.brex.testing.project.BrexProject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** {@code brex replay}: print a run's timeline chronologically. */
public final class ReplayCommand implements Command {

    @Override
    public String name() {
        return "replay";
    }

    @Override
    public String summary() {
        return "Step through a recorded run or match chronologically";
    }

    @Override
    public String usage() {
        return "brex replay <run> [--phase <name>] [--from <s>] [--to <s>] [--step <s>] [--only <kinds>]";
    }

    @Override
    public String help() {
        return """
                Prints phases, events, mechanism state changes and log entries in order. With --step,
                the robot's pose is printed at regular intervals as well.

                <run> is a run file, a run ID or fragment, a routine name (its latest run),
                #number, or 'latest'.

                Examples:
                  brex replay latest
                  brex replay match-038 --phase teleop --step 5
                  brex replay blue-left --only events,states
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(
                OptionSpec.value("phase", "name", "Only show one phase, e.g. autonomous or teleop"),
                OptionSpec.value("from", "s", "Start time in seconds"),
                OptionSpec.value("to", "s", "End time in seconds"),
                OptionSpec.value("step", "s", "Also print the robot pose every <s> seconds"),
                OptionSpec.value("only", "kinds", "Comma-separated: phases, events, states, log"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) {
        arguments.requireAtMostPositionals(1);
        BrexProject project = CommandSupport.openProject(context);
        Run run = CommandSupport.resolveRun(context, project, arguments.positional(0, "run"));
        ReplaySession session = new ReplaySession(run);

        double from = arguments.doubleValue("from", 0);
        double to = arguments.doubleValue("to", run.duration());
        if (arguments.value("phase").isPresent()) {
            String phaseName = arguments.value("phase").get();
            MatchPhase phase = run.phase(phaseName);
            if (phase == null) {
                try {
                    session.seekPhase(phaseName);
                } catch (IllegalArgumentException e) {
                    throw new CliException(e.getMessage());
                }
            } else {
                from = phase.start();
                to = phase.end();
            }
        }
        if (to < from) {
            throw CliException.usage("--to (" + to + ") must not be before --from (" + from + ")");
        }
        Set<Kind> kinds = parseKinds(arguments.value("only").orElse(null));
        double step = arguments.doubleValue("step", 0);
        if (step < 0) {
            throw CliException.usage("--step must be positive");
        }

        List<TimelineEntry> entries = session.timeline().only(kinds).between(from, to);
        List<RobotSnapshot> snapshots = new ArrayList<>();
        if (step > 0) {
            for (double t = from; t <= to + 1e-9; t += step) {
                snapshots.add(session.snapshotAt(t));
            }
        }

        if (context.json()) {
            context.out().println(JsonReports.write(json(run, entries, snapshots)));
            return ExitCode.OK;
        }
        print(context, project, run, entries, snapshots, to);
        return ExitCode.OK;
    }

    private static void print(CommandContext context, BrexProject project, Run run, List<TimelineEntry> entries,
            List<RobotSnapshot> snapshots, double to) {
        Style style = context.style();
        DistanceUnit unit = CommandSupport.distanceUnit(project);
        String commit = run.metadata().gitCommit() == null ? "" : " · commit " + Fmt.shortCommit(run.metadata().gitCommit());
        context.out().println(style.bold(CommandSupport.runLabel(project, run) + "  " + run.name()) + "  "
                + style.dim(run.id()));
        context.out().println(style.dim(Fmt.status(run) + " in " + Fmt.seconds(run.duration()) + " · "
                + Fmt.date(run.metadata().startedAtEpochMillis()) + commit));
        context.out().println();

        int e = 0;
        int s = 0;
        while (e < entries.size() || s < snapshots.size()) {
            boolean takeSnapshot = e >= entries.size()
                    || (s < snapshots.size() && snapshots.get(s).time() < entries.get(e).time());
            if (takeSnapshot) {
                RobotSnapshot snapshot = snapshots.get(s++);
                if (snapshot.pose() != null) {
                    context.out().println(time(snapshot.time()) + style.dim("· pose " + Fmt.pose(snapshot.pose(), unit)
                            + "  speed " + Fmt.speed(snapshot.velocity().speed(), unit)));
                }
            } else {
                context.out().println(time(entries.get(e).time()) + describe(style, entries.get(e++)));
            }
        }
        if (to >= run.duration()) {
            String end = "■ end (" + Fmt.status(run) + ")";
            if (run.endPose() != null) {
                end += "  pose " + Fmt.pose(run.endPose(), unit);
            }
            context.out().println(time(run.duration()) + style.bold(end));
        }
    }

    private static String describe(Style style, TimelineEntry entry) {
        return switch (entry.kind()) {
            case PHASE_START -> style.cyan("▶ phase " + entry.subject());
            case PHASE_END -> style.cyan("■ phase " + entry.subject() + " ended");
            case EVENT -> "● " + entry.subject() + (entry.detail().isEmpty() ? "" : "  " + style.dim(entry.detail()));
            case MECHANISM_STATE -> "◆ " + Fmt.pad(entry.subject(), 12)
                    + (entry.previous() == null ? entry.detail() : entry.previous() + " → " + entry.detail());
            case LOG -> switch (entry.subject()) {
                case "ERROR" -> style.red("✗ error: " + entry.detail());
                case "WARNING" -> style.yellow("! warning: " + entry.detail());
                default -> "i " + entry.detail();
            };
        };
    }

    private static String time(double seconds) {
        return Fmt.padLeft(Fmt.seconds(seconds), 8) + "  ";
    }

    private static Set<Kind> parseKinds(String value) {
        if (value == null) {
            return EnumSet.allOf(Kind.class);
        }
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        for (String item : value.split(",")) {
            switch (item.trim().toLowerCase(Locale.ROOT)) {
                case "phases" -> {
                    kinds.add(Kind.PHASE_START);
                    kinds.add(Kind.PHASE_END);
                }
                case "events" -> kinds.add(Kind.EVENT);
                case "states", "mechanisms" -> kinds.add(Kind.MECHANISM_STATE);
                case "log" -> kinds.add(Kind.LOG);
                default -> throw CliException.usage("Unknown kind '" + item.trim()
                        + "' for --only (expected phases, events, states or log)");
            }
        }
        return kinds;
    }

    private static Map<String, Object> json(Run run, List<TimelineEntry> entries, List<RobotSnapshot> snapshots) {
        Map<String, Object> root = JsonReports.envelope("replay");
        root.put("run", JsonReports.run(run));
        List<Object> timeline = new ArrayList<>();
        for (TimelineEntry entry : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", entry.time());
            m.put("kind", entry.kind().name());
            m.put("subject", entry.subject());
            m.put("detail", entry.detail());
            m.put("previous", entry.previous());
            timeline.add(m);
        }
        root.put("timeline", timeline);
        List<Object> frames = new ArrayList<>();
        for (RobotSnapshot snapshot : snapshots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", snapshot.time());
            m.put("pose", JsonReports.pose(snapshot.pose()));
            m.put("speed", snapshot.velocity() == null ? null : snapshot.velocity().speed());
            m.put("mechanismStates", snapshot.mechanismStates());
            m.put("telemetry", snapshot.numbers());
            m.put("phase", snapshot.phase());
            frames.add(m);
        }
        root.put("snapshots", frames);
        return root;
    }
}
