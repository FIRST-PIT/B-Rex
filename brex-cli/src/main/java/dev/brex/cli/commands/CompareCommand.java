package dev.brex.cli.commands;

import dev.brex.analysis.compare.GhostSample;
import dev.brex.analysis.compare.OccurrenceMatch;
import dev.brex.analysis.compare.RunComparison;
import dev.brex.analysis.compare.TrajectoryComparison;
import dev.brex.cli.Arguments;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.CommandSupport;
import dev.brex.cli.ExitCode;
import dev.brex.cli.Fmt;
import dev.brex.cli.JsonReports;
import dev.brex.cli.OptionSpec;
import dev.brex.cli.Style;
import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.run.Run;
import dev.brex.testing.project.BrexProject;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code brex compare}: ghost-run comparison of two runs. */
public final class CompareCommand implements Command {

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public String summary() {
        return "Compare a run against an earlier run (ghost run)";
    }

    @Override
    public String usage() {
        return "brex compare <earlier-run> <run> [--samples]";
    }

    @Override
    public String help() {
        return """
                Shows how <run> differs from <earlier-run>: duration, pose error at the same moment,
                deviation from the earlier path, heading, speed, final pose, and the timing of every
                event and mechanism state.

                "Pose error" compares both robots at the same time since start. "Path deviation"
                compares each pose with the closest point of the earlier path, so it measures
                accuracy even when one run is slower.

                Examples:
                  brex compare 41 42
                  brex compare .brex/baselines/blue-left.run.json latest
                  brex compare 41 42 --format json --samples > ghost.json
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(OptionSpec.flag("samples", "Include per-sample ghost data in JSON output"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) {
        arguments.requireAtMostPositionals(2);
        BrexProject project = CommandSupport.openProject(context);
        Run earlier = CommandSupport.resolveRun(context, project, arguments.positional(0, "earlier-run"));
        Run current = CommandSupport.resolveRun(context, project, arguments.positional(1, "run"));
        if (!earlier.name().equals(current.name())) {
            context.err().println(context.style().yellow("warning: ") + "comparing runs of different routines ("
                    + earlier.name() + " vs " + current.name() + ")");
        }
        RunComparison comparison = RunComparison.compare(earlier, current);
        if (context.json()) {
            context.out().println(JsonReports.write(json(comparison, arguments.flag("samples"))));
        } else {
            print(context, project, comparison);
        }
        return ExitCode.OK;
    }

    private static void print(CommandContext context, BrexProject project, RunComparison c) {
        PrintStream out = context.out();
        Style style = context.style();
        DistanceUnit unit = CommandSupport.distanceUnit(project);
        out.println(style.bold(CommandSupport.runLabel(project, c.baseline()) + "  vs  "
                + CommandSupport.runLabel(project, c.candidate())) + "   " + c.candidate().name());
        out.println(style.dim("  earlier " + c.baseline().id()));
        out.println(style.dim("  current " + c.candidate().id()));
        out.println();

        row(out, "Duration", Fmt.signedSeconds(c.durationDelta()), Fmt.seconds(c.baseline().duration()) + " → "
                + Fmt.seconds(c.candidate().duration()));
        TrajectoryComparison t = c.trajectory();
        if (t.isEmpty()) {
            row(out, "Trajectory", "not recorded", "");
        } else {
            row(out, "Max pose error", Fmt.distance(t.maxPositionError(), unit), "at the same moment");
            row(out, "Avg pose error", Fmt.distance(t.meanPositionError(), unit), "");
            row(out, "Path deviation", Fmt.distance(t.meanPathDeviation(), unit),
                    "avg · max " + Fmt.distance(t.maxPathDeviation(), unit));
            row(out, "Heading error", Fmt.degrees(t.meanHeadingError()), "avg · max " + Fmt.degrees(t.maxHeadingError()));
            row(out, "Speed difference", Fmt.speed(t.meanSpeedDifference(), unit), "avg at the same place");
            double offset = t.finalTimingOffset();
            row(out, "Path timing", Fmt.signedSeconds(offset),
                    offset > 0 ? "reached the end of the path later" : offset < 0 ? "reached the end sooner" : "");
        }
        if (!Double.isNaN(c.finalPositionError())) {
            row(out, "Final pose", Fmt.distance(c.finalPositionError(), unit), Fmt.signedDegrees(c.finalHeadingError()));
        }
        printMatches(out, style, "EVENTS", c.events(), false);
        printMatches(out, style, "MECHANISM STATES", c.mechanismStates(), true);
    }

    private static void printMatches(PrintStream out, Style style, String title, List<OccurrenceMatch> matches,
            boolean mechanisms) {
        if (matches.isEmpty()) {
            return;
        }
        out.println();
        out.println(style.bold(Fmt.pad(title, 30) + Fmt.padLeft("EARLIER", 9) + Fmt.padLeft("CURRENT", 10)
                + Fmt.padLeft("DELTA", 10)));
        for (OccurrenceMatch match : matches) {
            String label = mechanisms ? match.label().replaceFirst(":", " → ") : match.label();
            String earlier = Double.isNaN(match.baselineTime()) ? "-" : Fmt.seconds(match.baselineTime());
            String line = "  " + Fmt.pad(label, 28) + Fmt.padLeft(earlier, 9);
            switch (match.status()) {
                case MATCHED -> {
                    String delta = Fmt.padLeft(Fmt.signedSeconds(match.delta()), 10);
                    line += Fmt.padLeft(Fmt.seconds(match.candidateTime()), 10)
                            + (match.delta() > 0.005 ? style.yellow(delta) : delta);
                }
                case MISSING -> line += style.red(Fmt.padLeft("missing", 10));
                case EXTRA -> line += Fmt.padLeft(Fmt.seconds(match.candidateTime()), 10) + Fmt.padLeft("new", 10);
            }
            out.println(line);
        }
    }

    private static void row(PrintStream out, String label, String value, String detail) {
        out.println(Fmt.pad(label, 18) + Fmt.padLeft(value, 9) + (detail.isEmpty() ? "" : "   " + detail));
    }

    private static Map<String, Object> json(RunComparison c, boolean samples) {
        Map<String, Object> root = JsonReports.envelope("compare");
        root.put("earlier", JsonReports.run(c.baseline()));
        root.put("current", JsonReports.run(c.candidate()));
        root.put("durationDelta", c.durationDelta());
        TrajectoryComparison t = c.trajectory();
        Map<String, Object> trajectory = new LinkedHashMap<>();
        trajectory.put("recorded", !t.isEmpty());
        trajectory.put("maxPoseError", t.maxPositionError());
        trajectory.put("meanPoseError", t.meanPositionError());
        trajectory.put("maxPathDeviation", t.maxPathDeviation());
        trajectory.put("meanPathDeviation", t.meanPathDeviation());
        trajectory.put("maxHeadingError", t.maxHeadingError());
        trajectory.put("meanHeadingError", t.meanHeadingError());
        trajectory.put("meanSpeedDifference", t.meanSpeedDifference());
        trajectory.put("finalTimingOffset", t.finalTimingOffset());
        root.put("trajectory", trajectory);
        root.put("finalPositionError", c.finalPositionError());
        root.put("finalHeadingError", c.finalHeadingError());
        root.put("events", matches(c.events()));
        root.put("mechanismStates", matches(c.mechanismStates()));
        if (samples) {
            List<Object> list = new ArrayList<>();
            for (GhostSample s : t.samples()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("t", s.time());
                m.put("current", JsonReports.pose(s.candidate()));
                m.put("ghost", JsonReports.pose(s.ghost()));
                m.put("poseError", s.positionError());
                m.put("headingError", s.headingError());
                m.put("pathDeviation", s.pathDeviation());
                m.put("timingOffset", s.timingOffset());
                m.put("speedDifference", s.speedDifference());
                list.add(m);
            }
            root.put("samples", list);
        }
        return root;
    }

    private static List<Object> matches(List<OccurrenceMatch> matches) {
        List<Object> list = new ArrayList<>();
        for (OccurrenceMatch match : matches) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", match.key());
            m.put("occurrence", match.occurrence());
            m.put("status", match.status().name());
            m.put("earlierTime", match.baselineTime());
            m.put("currentTime", match.candidateTime());
            m.put("delta", match.delta());
            list.add(m);
        }
        return list;
    }
}
