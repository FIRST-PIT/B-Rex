package dev.brex.cli.commands;

import dev.brex.analysis.regression.RegressionCheck;
import dev.brex.cli.Arguments;
import dev.brex.cli.CliException;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.CommandSupport;
import dev.brex.cli.ExitCode;
import dev.brex.cli.Fmt;
import dev.brex.cli.JsonReports;
import dev.brex.cli.OptionSpec;
import dev.brex.cli.Reports;
import dev.brex.cli.Style;
import dev.brex.core.geometry.DistanceUnit;
import dev.brex.core.run.Run;
import dev.brex.testing.project.BrexProject;
import dev.brex.testing.suite.RegressionSuite;
import dev.brex.testing.suite.RegressionSuite.RoutineResult;
import dev.brex.testing.suite.RegressionSuite.Status;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code brex test}: regression-test the latest runs against baselines. */
public final class TestCommand implements Command {

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String summary() {
        return "Test the latest runs against their baselines";
    }

    @Override
    public String usage() {
        return "brex test [routine...] [--run <run>] [--ci] [--format json] [--quiet]";
    }

    @Override
    public String help() {
        return """
                For every routine with a baseline (or only the routines given), compares its latest
                recorded run against the baseline: duration, path accuracy, final pose,
                localization, expected events, event and mechanism timing, and reliability.
                Thresholds come from brex.properties.

                Exit codes: 0 all tests passed, 1 a test failed or could not run,
                            2 invalid command line, 3 B-rex error.

                Examples:
                  brex test
                  brex test blue-left --run 3fa9
                  brex test --ci --format json > brex-results.json
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(OptionSpec.value("run", "run", "Test this run instead of the latest (one routine only)"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) {
        BrexProject project = CommandSupport.requireProject(context);
        List<String> routines = arguments.positionals();
        Run override = arguments.value("run").map(ref -> CommandSupport.resolveRun(context, project, ref)).orElse(null);
        if (override != null) {
            if (routines.size() > 1) {
                throw CliException.usage("--run can only be used with a single routine");
            }
            if (routines.isEmpty()) {
                routines = List.of(override.name());
            }
        }
        RegressionSuite.Result result;
        try {
            result = RegressionSuite.run(project, routines, override);
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage());
        }
        if (result.routines().isEmpty()) {
            throw new CliException("Nothing to test: no baselines in "
                    + project.root().relativize(project.baselines().directory())
                    + ". Save one with 'brex baseline save <routine>'.", ExitCode.FAILED);
        }
        if (context.json()) {
            context.out().println(JsonReports.write(json(result)));
        } else {
            print(context, project, result);
        }
        return result.passed() ? ExitCode.OK : ExitCode.FAILED;
    }

    private static void print(CommandContext context, BrexProject project, RegressionSuite.Result result) {
        PrintStream out = context.out();
        Style style = context.style();
        DistanceUnit unit = CommandSupport.distanceUnit(project);
        for (RoutineResult routine : result.routines()) {
            out.println(headline(style, routine));
            if (context.quiet()) {
                continue;
            }
            if (routine.report() == null) {
                out.println("  " + routine.message());
                out.println();
                continue;
            }
            out.println(style.dim("  run " + routine.candidate().id() + " vs baseline " + routine.baseline().id()));
            if (routine.status() == Status.FAILED) {
                printComparison(out, style, routine);
            }
            out.println();
            for (RegressionCheck check : routine.report().checks()) {
                Reports.printCheck(out, style, check, unit);
            }
            out.println();
            out.println("  " + Reports.scoreLine(routine.score()));
            out.println();
        }
        long passed = result.count(Status.PASSED);
        long failed = result.count(Status.FAILED);
        long skipped = result.count(Status.SKIPPED);
        long errors = result.count(Status.ERROR);
        out.println(Fmt.rule(40));
        out.println(passed + " passed, " + failed + " failed, " + skipped + " skipped" + (errors > 0 ? ", " + errors
                + " could not run" : ""));
        out.println(result.passed() ? style.green("✓ Tests passed") : style.red("✗ Tests failed"));
    }

    private static String headline(Style style, RoutineResult routine) {
        return switch (routine.status()) {
            case PASSED -> style.green("✓") + " " + style.bold(routine.routine()) + "  passed";
            case FAILED -> style.red("✗") + " " + style.bold(routine.routine()) + "  " + style.red("REGRESSION DETECTED");
            case SKIPPED -> "- " + style.bold(routine.routine()) + "  skipped" + (routine.report() == null
                    ? ": " + routine.message() : "");
            case ERROR -> style.red("✗") + " " + style.bold(routine.routine()) + "  could not run";
        };
    }

    /** The "Previous / Current" block for a failed routine. */
    private static void printComparison(PrintStream out, Style style, RoutineResult routine) {
        double previous = routine.baseline().duration();
        double current = routine.candidate().duration();
        double delta = current - previous;
        out.println();
        out.println("  Previous: " + Fmt.seconds(previous));
        out.println("  Current:  " + Fmt.seconds(current));
        String change = Fmt.signedSeconds(delta) + (delta > 0 ? " slower" : delta < 0 ? " faster" : "");
        out.println("  " + (delta > 0 ? style.bold(change) : change));
    }

    private static Map<String, Object> json(RegressionSuite.Result result) {
        Map<String, Object> root = JsonReports.envelope("test");
        root.put("passed", result.passed());
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            summary.put(status.name().toLowerCase(java.util.Locale.ROOT), result.count(status));
        }
        root.put("summary", summary);
        List<Object> routines = new ArrayList<>();
        for (RoutineResult routine : result.routines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routine", routine.routine());
            m.put("status", routine.status().name());
            m.put("message", routine.message());
            m.put("baseline", JsonReports.run(routine.baseline()));
            m.put("candidate", JsonReports.run(routine.candidate()));
            m.put("checks", JsonReports.checks(routine.report()));
            m.put("score", JsonReports.score(routine.score()));
            routines.add(m);
        }
        root.put("routines", routines);
        return root;
    }
}
