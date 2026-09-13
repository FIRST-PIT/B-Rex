package dev.brex.cli.commands;

import dev.brex.analysis.compare.GhostSample;
import dev.brex.analysis.compare.OccurrenceMatch;
import dev.brex.analysis.compare.RunComparison;
import dev.brex.analysis.compare.TrajectoryComparison;
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
import dev.brex.core.run.Run;
import dev.brex.git.Commit;
import dev.brex.git.CommitStats;
import dev.brex.git.Git;
import dev.brex.testing.project.BrexProject;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

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
        return "brex compare <earlier-run> <run> [--samples]\n"
                + "       brex compare <revision> <revision>";
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

                When both arguments are Git revisions rather than runs, every routine with runs
                recorded at either commit is compared: run count, mean duration, reliability and
                mean score.

                  brex compare HEAD~5 HEAD
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
        String first = arguments.positional(0, "earlier-run");
        String second = arguments.positional(1, "run");
        Run earlier;
        Run current;
        try {
            earlier = CommandSupport.resolveRun(context, project, first);
            current = CommandSupport.resolveRun(context, project, second);
        } catch (CliException runError) {
            Optional<Git> git = Git.open(context.workingDirectory());
            Optional<Commit> a = git.flatMap(g -> g.tryResolve(first));
            Optional<Commit> b = git.flatMap(g -> g.tryResolve(second));
            if (a.isPresent() && b.isPresent()) {
                return compareCommits(context, project, first, a.get(), second, b.get());
            }
            throw runError;
        }
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

    private static int compareCommits(CommandContext context, BrexProject project, String revA, Commit a,
            String revB, Commit b) {
        if (!project.isInitialized()) {
            throw new CliException("Not a B-rex project: no brex.properties or .brex directory. Run 'brex init' first.");
        }
        List<Run> runs = project.runs().runs();
        TreeSet<String> routines = new TreeSet<>();
        for (Run run : runs) {
            if (a.matches(run.metadata().gitCommit()) || b.matches(run.metadata().gitCommit())) {
                routines.add(run.name());
            }
        }
        if (routines.isEmpty()) {
            throw new CliException("No runs recorded at " + a.shortHash() + " or " + b.shortHash() + ". Runs know "
                    + "their commit when recorded with gitCommit(...) or imported with "
                    + "'brex run import --commit <revision>'.");
        }
        List<CommitStats[]> stats = new ArrayList<>();
        for (String routine : routines) {
            Run baseline = project.baseline(routine).orElse(null);
            stats.add(new CommitStats[] {
                CommitStats.of(routine, a, CommitStats.runsAt(a, routine, runs), baseline,
                        project.scoringConfig(routine), project.idleStates()),
                CommitStats.of(routine, b, CommitStats.runsAt(b, routine, runs), baseline,
                        project.scoringConfig(routine), project.idleStates())});
        }
        if (context.json()) {
            Map<String, Object> root = JsonReports.envelope("compare commits");
            root.put("earlier", commitJson(revA, a));
            root.put("current", commitJson(revB, b));
            List<Object> list = new ArrayList<>();
            for (CommitStats[] pair : stats) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("routine", pair[0].routine());
                m.put("earlier", statsJson(pair[0]));
                m.put("current", statsJson(pair[1]));
                list.add(m);
            }
            root.put("routines", list);
            context.out().println(JsonReports.write(root));
            return ExitCode.OK;
        }
        PrintStream out = context.out();
        Style style = context.style();
        out.println(style.bold(a.shortHash()) + "  " + Fmt.pad(revA, 8) + " " + a.subject());
        out.println(style.bold(b.shortHash()) + "  " + Fmt.pad(revB, 8) + " " + b.subject());
        for (CommitStats[] pair : stats) {
            CommitStats x = pair[0];
            CommitStats y = pair[1];
            out.println();
            out.println(style.bold(Fmt.pad(x.routine(), 20)) + Fmt.padLeft(a.shortHash(), 10)
                    + Fmt.padLeft(b.shortHash(), 11) + Fmt.padLeft("change", 11));
            commitRow(out, "Runs", Integer.toString(x.runs()), Integer.toString(y.runs()), "");
            commitRow(out, "Duration", Fmt.seconds(x.meanDuration()), Fmt.seconds(y.meanDuration()),
                    change(x.meanDuration(), y.meanDuration(), "%+.2fs"));
            commitRow(out, "Reliability", Fmt.percent(x.reliability()), Fmt.percent(y.reliability()),
                    change(x.reliability(), y.reliability(), "%+.1f%%"));
            commitRow(out, "Score", Fmt.score(x.meanScore()), Fmt.score(y.meanScore()),
                    change(x.meanScore(), y.meanScore(), "%+.1f"));
        }
        return ExitCode.OK;
    }

    private static void commitRow(PrintStream out, String label, String earlier, String current, String change) {
        out.println("  " + Fmt.pad(label, 18) + Fmt.padLeft(earlier, 10) + Fmt.padLeft(current, 11)
                + Fmt.padLeft(change, 11));
    }

    private static String change(double earlier, double current, String pattern) {
        return Double.isNaN(earlier) || Double.isNaN(current) ? ""
                : String.format(java.util.Locale.ROOT, pattern, current - earlier);
    }

    private static Map<String, Object> commitJson(String revision, Commit commit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revision", revision);
        m.put("hash", commit.hash());
        m.put("subject", commit.subject());
        return m;
    }

    private static Map<String, Object> statsJson(CommitStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runs", s.runs());
        m.put("meanDuration", s.meanDuration());
        m.put("reliability", s.reliability());
        m.put("meanScore", s.meanScore());
        return m;
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
