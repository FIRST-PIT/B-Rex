package dev.brex.cli.commands;

import dev.brex.analysis.score.AutonScore;
import dev.brex.analysis.score.AutonScorer;
import dev.brex.cli.Arguments;
import dev.brex.cli.CliException;
import dev.brex.cli.Command;
import dev.brex.cli.CommandContext;
import dev.brex.cli.CommandSupport;
import dev.brex.cli.ExitCode;
import dev.brex.cli.Fmt;
import dev.brex.cli.JsonReports;
import dev.brex.cli.OptionSpec;
import dev.brex.cli.ProcessRunner;
import dev.brex.cli.Reports;
import dev.brex.cli.Style;
import dev.brex.core.format.RunFormat;
import dev.brex.core.format.RunReader;
import dev.brex.core.run.Run;
import dev.brex.recording.RunStore;
import dev.brex.testing.project.BrexProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** {@code brex run}: list, inspect, import and pull recorded runs. */
public final class RunCommand implements Command {

    private final ProcessRunner processes;

    public RunCommand(ProcessRunner processes) {
        this.processes = processes;
    }

    @Override
    public String name() {
        return "run";
    }

    @Override
    public String summary() {
        return "List, show, import or pull recorded runs";
    }

    @Override
    public String usage() {
        return "brex run list [routine] [--limit <N>]\n"
                + "       brex run show <run>\n"
                + "       brex run import <file|directory>... [--commit <hash>]\n"
                + "       brex run pull [--device-dir <dir>] [--adb <path>] [--commit <hash>]";
    }

    @Override
    public String help() {
        return """
                Runs recorded on the robot are saved to /sdcard/FIRST/brex/runs. 'pull' copies new
                runs from a connected Control Hub (USB or Wi-Fi via 'adb connect 192.168.43.1:5555')
                into .brex/runs; 'import' copies run files you already have.

                --commit stamps imported runs with the Git commit the robot code was built from,
                for runs that did not record it themselves.

                Examples:
                  brex run pull
                  brex run list blue-left
                  brex run show latest
                """;
    }

    @Override
    public List<OptionSpec> options() {
        return List.of(
                OptionSpec.value("limit", "N", "Show at most N runs (default 20)"),
                OptionSpec.value("commit", "hash", "Stamp imported runs with this Git commit"),
                OptionSpec.value("device-dir", "dir", "Run directory on the robot (default "
                        + RunStore.ROBOT_DIRECTORY.getPath() + ")"),
                OptionSpec.value("adb", "path", "adb executable (default: $ADB, Android SDK, or adb on PATH)"));
    }

    @Override
    public int execute(CommandContext context, Arguments arguments) throws Exception {
        String action = arguments.positional(0, "list|show|import|pull");
        BrexProject project = CommandSupport.requireProject(context);
        return switch (action) {
            case "list" -> list(context, arguments, project);
            case "show" -> show(context, arguments, project);
            case "import" -> importRuns(context, arguments, project);
            case "pull" -> pull(context, arguments, project);
            default -> throw CliException.usage("Unknown run action '" + action
                    + "' (expected list, show, import or pull)");
        };
    }

    private int list(CommandContext context, Arguments arguments, BrexProject project) {
        arguments.requireAtMostPositionals(2);
        RunStore.Scan scan = project.runs().scan();
        for (Map.Entry<File, String> failure : scan.failures().entrySet()) {
            context.err().println(context.style().yellow("warning: ") + "skipped " + failure.getKey().getName() + ": "
                    + failure.getValue());
        }
        Optional<String> routine = arguments.optionalPositional(1);
        int limit = arguments.intValue("limit", 20);
        List<Run> all = scan.runs();
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (routine.isEmpty() || all.get(i).name().equals(routine.get())) {
                numbers.add(i + 1);
            }
        }
        List<Integer> shown = numbers.subList(Math.max(0, numbers.size() - limit), numbers.size());
        if (context.json()) {
            List<Object> items = new ArrayList<>();
            for (int number : shown) {
                Map<String, Object> run = JsonReports.run(all.get(number - 1));
                run.put("number", number);
                items.add(run);
            }
            context.out().println(JsonReports.write(items));
            return ExitCode.OK;
        }
        if (shown.isEmpty()) {
            context.out().println(routine.map(r -> "No runs of '" + r + "'.").orElse("No runs yet.")
                    + " Pull them from the robot with 'brex run pull'.");
            return ExitCode.OK;
        }
        Style style = context.style();
        context.out().println(style.bold(Fmt.padLeft("#", 4) + "  " + Fmt.pad("RUN", 36) + Fmt.pad("ROUTINE", 16)
                + Fmt.pad("STATUS", 12) + Fmt.padLeft("DURATION", 9) + "  COMMIT"));
        for (int number : shown) {
            Run run = all.get(number - 1);
            String status = Fmt.pad(Fmt.status(run), 12);
            context.out().println(Fmt.padLeft(Integer.toString(number), 4) + "  " + Fmt.pad(run.id(), 36)
                    + Fmt.pad(run.name(), 16) + (run.succeeded() ? status : style.yellow(status))
                    + Fmt.padLeft(Fmt.seconds(run.duration()), 9) + "  " + Fmt.shortCommit(run.metadata().gitCommit()));
        }
        if (numbers.size() > shown.size()) {
            context.info(style.dim((numbers.size() - shown.size()) + " older run(s) not shown; use --limit"));
        }
        return ExitCode.OK;
    }

    private int show(CommandContext context, Arguments arguments, BrexProject project) {
        arguments.requireAtMostPositionals(2);
        Run run = CommandSupport.resolveRun(context, project, arguments.positional(1, "run"));
        Optional<Run> baseline = project.baseline(run.name()).filter(b -> !b.id().equals(run.id()));
        List<Run> history = project.history(run.name()).stream()
                .filter(r -> r.metadata().startedAtEpochMillis() <= run.metadata().startedAtEpochMillis()).toList();
        AutonScore score = new AutonScorer(project.scoringConfig(run.name()), project.idleStates())
                .score(run, baseline.orElse(null), history);
        if (context.json()) {
            Map<String, Object> root = JsonReports.envelope("run show");
            root.put("run", JsonReports.run(run));
            root.put("baseline", baseline.map(Run::id).orElse(null));
            root.put("score", JsonReports.score(score));
            context.out().println(JsonReports.write(root));
            return ExitCode.OK;
        }
        CommandSupport.printRunSummary(context, project, run);
        for (var warning : run.warnings()) {
            context.out().println("  " + context.style().yellow("! ") + Fmt.seconds(warning.time()) + " "
                    + warning.message());
        }
        for (var error : run.errors()) {
            context.out().println("  " + context.style().red("✗ ") + Fmt.seconds(error.time()) + " " + error.message());
        }
        context.out().println();
        context.out().println(context.style().dim(baseline.map(b -> "Compared with baseline " + b.id())
                .orElse("No baseline for " + run.name() + "; accuracy and mechanism scores need one")));
        Reports.printScore(context.out(), context.style(), score);
        return ExitCode.OK;
    }

    private int importRuns(CommandContext context, Arguments arguments, BrexProject project) throws IOException {
        List<String> paths = arguments.positionals().subList(1, arguments.positionals().size());
        if (paths.isEmpty()) {
            throw CliException.usage("Missing argument <file|directory>");
        }
        List<Path> files = new ArrayList<>();
        for (String path : paths) {
            Path resolved = context.workingDirectory().resolve(path);
            if (Files.isDirectory(resolved)) {
                files.addAll(runFiles(resolved));
            } else if (Files.isRegularFile(resolved)) {
                files.add(resolved);
            } else {
                throw new CliException("No such file or directory: " + resolved);
            }
        }
        return importFiles(context, project, files, arguments.value("commit").orElse(null));
    }

    private int pull(CommandContext context, Arguments arguments, BrexProject project) throws Exception {
        arguments.requireAtMostPositionals(1);
        String adb = arguments.value("adb").orElseGet(() -> locateAdb(context.env()));
        String deviceDir = arguments.value("device-dir").orElse(RunStore.ROBOT_DIRECTORY.getPath());
        Path staging = project.root().resolve(BrexProject.DATA_DIRECTORY).resolve("pull");
        deleteRecursively(staging);
        Files.createDirectories(staging);
        try {
            context.info("Pulling runs from " + deviceDir + " ...");
            ProcessRunner.Result result;
            try {
                result = processes.run(List.of(adb, "pull", deviceDir + "/.", staging.toString()), project.root());
            } catch (IOException e) {
                throw new CliException("Could not run adb (" + adb + "). Install Android platform-tools or pass "
                        + "--adb <path>.");
            }
            if (result.exitCode() != 0) {
                throw new CliException("adb pull failed: " + result.output().trim() + System.lineSeparator()
                        + "Is the robot connected? Check with 'adb devices'; over Wi-Fi, run "
                        + "'adb connect 192.168.43.1:5555' first.");
            }
            return importFiles(context, project, runFiles(staging), arguments.value("commit").orElse(null));
        } finally {
            deleteRecursively(staging);
        }
    }

    private static int importFiles(CommandContext context, BrexProject project, List<Path> files, String commit) {
        int imported = 0;
        int present = 0;
        int failed = 0;
        for (Path file : files) {
            Run run;
            try {
                run = RunReader.read(file.toFile());
            } catch (IOException | RuntimeException e) {
                context.err().println(context.style().red("✗ ") + file.getFileName() + ": " + e.getMessage());
                failed++;
                continue;
            }
            if (commit != null) {
                run = run.withMetadata(run.metadata().toBuilder().gitCommit(commit).build());
            }
            if (project.runs().fileFor(run).exists()) {
                present++;
                continue;
            }
            try {
                project.runs().save(run);
            } catch (IOException e) {
                throw new CliException("Could not save " + run.id() + ": " + e.getMessage());
            }
            imported++;
            context.info(context.style().green("+ ") + run.id() + "  " + run.name() + "  " + Fmt.status(run) + "  "
                    + Fmt.seconds(run.duration()));
        }
        if (context.json()) {
            Map<String, Object> root = JsonReports.envelope("run import");
            root.put("imported", imported);
            root.put("alreadyPresent", present);
            root.put("failed", failed);
            context.out().println(JsonReports.write(root));
        } else {
            context.out().println("Imported " + imported + " run(s)" + (present > 0 ? ", " + present
                    + " already present" : "") + (failed > 0 ? ", " + failed + " unreadable" : ""));
        }
        return failed > 0 ? ExitCode.ERROR : ExitCode.OK;
    }

    private static List<Path> runFiles(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(RunFormat.EXTENSION)
                            || p.getFileName().toString().endsWith(RunFormat.COMPRESSED_EXTENSION))
                    .sorted()
                    .toList();
        }
    }

    static String locateAdb(Map<String, String> env) {
        if (env.containsKey("ADB")) {
            return env.get("ADB");
        }
        for (String sdkVariable : List.of("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            String sdk = env.get(sdkVariable);
            if (sdk != null) {
                Path adb = Path.of(sdk, "platform-tools", "adb");
                if (Files.isRegularFile(adb)) {
                    return adb.toString();
                }
            }
        }
        return "adb";
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
