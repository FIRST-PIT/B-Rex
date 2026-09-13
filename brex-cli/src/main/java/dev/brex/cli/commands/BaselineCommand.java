package dev.brex.cli.commands;

import dev.brex.cli.Arguments;
import dev.brex.cli.CliException;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.ExitCode;
import dev.brex.cli.Fmt;
import dev.brex.cli.OptionSpec;
import dev.brex.core.json.Json;
import dev.brex.core.run.Run;
import dev.brex.testing.project.BrexProject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code brex baseline}: manage known-good runs. */
public final class BaselineCommand implements Command {

    @Override
    public String name() {
        return "baseline";
    }

    @Override
    public String summary() {
        return "Save, list, show or delete known-good baselines";
    }

    @Override
    public String usage() {
        return "brex baseline save <routine> [--run <run>] [--force]\n"
                + "       brex baseline list\n"
                + "       brex baseline show <routine>\n"
                + "       brex baseline delete <routine>";
    }

    @Override
    public String help() {
        return """
                A baseline is a known-good run of a routine. 'brex test' compares the latest run of
                every routine that has a baseline against it. Commit .brex/baselines/ so your team
                and CI use the same reference.

                By default 'save' uses the latest run of the routine. Only runs that completed
                without errors can become baselines unless --force is given.

                Examples:
                  brex baseline save blue-left
                  brex baseline save blue-left --run 3fa9
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(
                OptionSpec.value("run", "run", "Run to save instead of the routine's latest run"),
                OptionSpec.flag("force", "Allow a run that did not complete cleanly"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) throws IOException {
        String action = arguments.positional(0, "save|list|show|delete");
        BrexProject project = dev.brex.cli.CommandSupport.requireProject(context);
        return switch (action) {
            case "save" -> save(context, arguments, project);
            case "list" -> list(context, project);
            case "show" -> show(context, arguments, project);
            case "delete" -> delete(context, arguments, project);
            default -> throw CliException.usage("Unknown baseline action '" + action
                    + "' (expected save, list, show or delete)");
        };
    }

    private int save(CommandContext context, Arguments arguments, BrexProject project) throws IOException {
        arguments.requireAtMostPositionals(2);
        String routine = arguments.positional(1, "routine");
        Run run = arguments.value("run")
                .map(ref -> dev.brex.cli.CommandSupport.resolveRun(context, project, ref))
                .orElseGet(() -> {
                    Run latest = project.runs().latest(routine);
                    if (latest == null) {
                        throw new CliException("No runs of '" + routine + "' to save as a baseline. Import or pull a "
                                + "run first (brex run import <file>, brex run pull).");
                    }
                    return latest;
                });
        if (!run.name().equals(routine)) {
            throw new CliException("Run " + run.id() + " is a run of '" + run.name() + "', not '" + routine + "'");
        }
        if (!run.succeeded() && !arguments.flag("force")) {
            throw new CliException("Run " + run.id() + " " + Fmt.status(run)
                    + (run.errors().isEmpty() ? "" : " with " + run.errors().size() + " error(s)")
                    + "; a baseline should be a known-good run. Use --force to save it anyway.");
        }
        Optional<Run> previous = project.baselines().load(routine);
        Path file = project.baselines().save(run);
        if (context.json()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("routine", routine);
            out.put("run", run.id());
            out.put("replaced", previous.map(Run::id).orElse(null));
            out.put("file", project.root().relativize(file).toString());
            context.out().println(Json.write(out, true));
            return ExitCode.OK;
        }
        context.out().println(context.style().green("✓") + " Saved baseline " + context.style().bold(routine)
                + " from run " + run.id() + " (" + Fmt.seconds(run.duration()) + ", " + Fmt.status(run) + ")");
        previous.filter(p -> !p.id().equals(run.id()))
                .ifPresent(p -> context.info("  replaced run " + p.id()));
        context.info("  " + project.root().relativize(file));
        context.info("  Commit this file so your team and CI test against the same baseline.");
        return ExitCode.OK;
    }

    private int list(CommandContext context, BrexProject project) throws IOException {
        List<Run> baselines = new ArrayList<>();
        for (String routine : project.baselines().routines()) {
            project.baselines().load(routine).ifPresent(baselines::add);
        }
        if (context.json()) {
            List<Object> items = new ArrayList<>();
            for (Run run : baselines) {
                items.add(dev.brex.cli.CommandSupport.runJson(run));
            }
            context.out().println(Json.write(items, true));
            return ExitCode.OK;
        }
        if (baselines.isEmpty()) {
            context.out().println("No baselines yet. Save one with 'brex baseline save <routine>'.");
            return ExitCode.OK;
        }
        context.out().println(context.style().bold(Fmt.pad("ROUTINE", 18) + Fmt.pad("RUN", 36) + Fmt.pad("DURATION", 10)
                + "COMMIT"));
        for (Run run : baselines) {
            context.out().println(Fmt.pad(run.name(), 18) + Fmt.pad(run.id(), 36)
                    + Fmt.pad(Fmt.seconds(run.duration()), 10) + Fmt.shortCommit(run.metadata().gitCommit()));
        }
        return ExitCode.OK;
    }

    private int show(CommandContext context, Arguments arguments, BrexProject project) throws IOException {
        String routine = arguments.positional(1, "routine");
        Run run = project.baselines().load(routine)
                .orElseThrow(() -> new CliException("No baseline for '" + routine + "'"));
        if (context.json()) {
            context.out().println(Json.write(dev.brex.cli.CommandSupport.runJson(run), true));
            return ExitCode.OK;
        }
        dev.brex.cli.CommandSupport.printRunSummary(context, project, run);
        return ExitCode.OK;
    }

    private int delete(CommandContext context, Arguments arguments, BrexProject project) throws IOException {
        String routine = arguments.positional(1, "routine");
        if (!project.baselines().delete(routine)) {
            throw new CliException("No baseline for '" + routine + "'");
        }
        context.info("Deleted baseline " + routine);
        return ExitCode.OK;
    }
}
