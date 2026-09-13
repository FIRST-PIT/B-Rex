package dev.brex.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Git working copy, accessed through the {@code git} executable. B-rex never requires Git:
 * {@link #open} returns empty when Git is missing or the directory is not a repository.
 */
public final class Git {

    /** Executes git with arguments in a directory and returns standard output. */
    @FunctionalInterface
    public interface Runner {
        String run(Path directory, List<String> arguments);
    }

    private static final String FIELD = "";

    private final Path root;
    private final Runner runner;

    private Git(Path root, Runner runner) {
        this.root = root;
        this.runner = runner;
    }

    /** Opens the repository containing {@code directory}, if any. */
    public static Optional<Git> open(Path directory) {
        return open(directory, Git::exec);
    }

    public static Optional<Git> open(Path directory, Runner runner) {
        try {
            String top = runner.run(directory, List.of("rev-parse", "--show-toplevel")).trim();
            return top.isEmpty() ? Optional.empty() : Optional.of(new Git(Path.of(top), runner));
        } catch (GitException e) {
            return Optional.empty();
        }
    }

    public Path root() {
        return root;
    }

    /** Resolves a revision such as {@code HEAD}, {@code HEAD~5}, {@code main} or a hash. */
    public Commit resolve(String revision) {
        return tryResolve(revision).orElseThrow(() -> new GitException("Unknown Git revision '" + revision + "'"));
    }

    public Optional<Commit> tryResolve(String revision) {
        if (revision.startsWith("-")) {
            return Optional.empty();
        }
        try {
            List<Commit> commits = parseLog(git("log", "-1", "--format=" + format(), revision + "^{commit}", "--"));
            return commits.isEmpty() ? Optional.empty() : Optional.of(commits.get(0));
        } catch (GitException e) {
            return Optional.empty();
        }
    }

    public Commit head() {
        return resolve("HEAD");
    }

    /** The current branch name, or null when HEAD is detached. */
    public String branch() {
        String branch = git("rev-parse", "--abbrev-ref", "HEAD").trim();
        return branch.equals("HEAD") ? null : branch;
    }

    /** Whether tracked or untracked files differ from HEAD. */
    public boolean isDirty() {
        return !git("status", "--porcelain").isBlank();
    }

    /** The most recent commits reachable from HEAD, newest first. */
    public List<Commit> log(int limit) {
        return parseLog(git("log", "-" + limit, "--format=" + format(), "HEAD", "--"));
    }

    private String git(String... arguments) {
        return runner.run(root, List.of(arguments));
    }

    private static String format() {
        return "%H%x1f%an%x1f%ct%x1f%s";
    }

    private static List<Commit> parseLog(String output) {
        List<Commit> commits = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(FIELD, 4);
            if (parts.length < 4) {
                throw new GitException("Unexpected git log output: " + line);
            }
            commits.add(new Commit(parts[0], parts[3], parts[1], Long.parseLong(parts[2]) * 1000));
        }
        return commits;
    }

    private static String exec(Path directory, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(arguments);
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
            byte[] out = process.getInputStream().readAllBytes();
            byte[] err = process.getErrorStream().readAllBytes();
            int code = process.waitFor();
            if (code != 0) {
                throw new GitException("git " + String.join(" ", arguments) + " failed: "
                        + new String(err, StandardCharsets.UTF_8).trim());
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GitException("Git is not available: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Interrupted while running git", e);
        }
    }
}
