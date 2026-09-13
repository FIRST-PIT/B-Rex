package dev.brex.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitTest {

    @TempDir
    Path repo;

    private Git git;

    private static boolean gitInstalled() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void sh(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IOException(String.join(" ", command) + ": " + output);
        }
    }

    private void commit(String file, String message) throws IOException, InterruptedException {
        Files.writeString(repo.resolve(file), message);
        sh("git", "add", file);
        sh("git", "-c", "user.name=Rex", "-c", "user.email=rex@example.com", "commit", "-q", "-m", message);
    }

    @BeforeEach
    void createRepository() throws IOException, InterruptedException {
        assumeTrue(gitInstalled(), "git is not installed");
        sh("git", "init", "-q", "-b", "main");
        commit("a.txt", "first");
        commit("b.txt", "tune lift PID");
        git = Git.open(repo).orElseThrow();
    }

    @Test
    void resolvesRevisions() {
        Commit head = git.head();
        Commit previous = git.resolve("HEAD~1");

        assertEquals(40, head.hash().length());
        assertEquals("tune lift PID", head.subject());
        assertEquals("Rex", head.author());
        assertEquals("first", previous.subject());
        assertNotEquals(head.hash(), previous.hash());
        assertEquals(head, git.resolve(head.shortHash()));
        assertTrue(git.tryResolve("HEAD~9").isEmpty());
        assertTrue(git.tryResolve("--help").isEmpty());
        assertEquals("Unknown Git revision 'nope'", assertThrows(GitException.class, () -> git.resolve("nope"))
                .getMessage());
    }

    @Test
    void reportsBranchDirtinessAndLog() throws IOException {
        assertEquals("main", git.branch());
        assertFalse(git.isDirty());

        Files.writeString(repo.resolve("a.txt"), "changed");

        assertTrue(git.isDirty());
        assertEquals(List.of("tune lift PID", "first"), git.log(5).stream().map(Commit::subject).toList());
    }

    @Test
    void openingNonRepositoryIsEmpty(@TempDir Path elsewhere) {
        assertTrue(Git.open(elsewhere).isEmpty());
        assertTrue(Git.open(elsewhere, (dir, args) -> {
            throw new GitException("git: command not found");
        }).isEmpty());
    }

    @Test
    void matchesAbbreviatedHashesSafely() {
        Commit commit = git.head();

        assertTrue(commit.matches(commit.shortHash()));
        assertTrue(commit.matches(commit.hash()));
        assertFalse(commit.matches(commit.hash().substring(0, 4)));
        assertFalse(commit.matches(null));
    }

    @Test
    void aggregatesRunsPerCommit() {
        Commit head = git.head();
        Commit previous = git.resolve("HEAD~1");
        List<Run> runs = List.of(
                run("1", previous.shortHash(), 22.81, RunStatus.COMPLETED),
                run("2", head.hash(), 22.0, RunStatus.COMPLETED),
                run("3", head.shortHash(), 22.2, RunStatus.COMPLETED),
                run("4", head.shortHash(), 30, RunStatus.INCOMPLETE));

        List<Run> atHead = CommitStats.runsAt(head, "blue-left", runs);
        CommitStats stats = CommitStats.of("blue-left", head, atHead, null, ScoringConfig.DEFAULTS.withTargetSeconds(22),
                IdleStates.DEFAULT);

        assertEquals(3, stats.runs());
        assertEquals(22.1, stats.meanDuration(), 1e-9);
        assertEquals(200.0 / 3, stats.reliability(), 1e-9);
        assertTrue(stats.meanScore() > 0);
        assertEquals(0, CommitStats.of("blue-left", head, List.of(), null, ScoringConfig.DEFAULTS, IdleStates.DEFAULT)
                .runs());
    }

    private static Run run(String id, String commit, double duration, RunStatus status) {
        return Run.builder(id, RunMetadata.builder("blue-left").gitCommit(commit).startedAtEpochMillis(Long.parseLong(id))
                .build()).status(status).duration(duration).build();
    }
}
