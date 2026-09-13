package dev.brex.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.time.ManualClock;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunStoreTest {

    @TempDir
    Path dir;

    private RunStore store;

    private static Run run(String id, String name, long startedAt) {
        return Run.builder(id, RunMetadata.builder(name).startedAtEpochMillis(startedAt).build()).duration(1).build();
    }

    @BeforeEach
    void setUp() throws IOException {
        store = new RunStore(dir.resolve("runs").toFile());
        store.save(run("20260913-100000-blue-left-aaaa", "blue-left", 1000));
        store.save(run("20260913-100100-red-right-bbbb", "red-right", 2000));
        store.save(run("20260913-100200-blue-left-cccc", "blue-left", 3000));
    }

    @Test
    void listsRunsChronologically() {
        assertEquals(List.of("20260913-100000-blue-left-aaaa", "20260913-100100-red-right-bbbb",
                "20260913-100200-blue-left-cccc"), store.runs().stream().map(Run::id).toList());
        assertEquals(2, store.runsOf("blue-left").size());
        assertEquals("20260913-100200-blue-left-cccc", store.latest("blue-left").id());
        assertNull(store.latest("never-ran"));
        assertEquals(2, store.ordinal(store.latest("red-right")));
    }

    @Test
    void resolvesReferences() {
        assertEquals("20260913-100200-blue-left-cccc", store.resolve("latest").id());
        assertEquals("20260913-100100-red-right-bbbb", store.resolve("#2").id());
        assertEquals("20260913-100100-red-right-bbbb", store.resolve("2").id());
        assertEquals("20260913-100000-blue-left-aaaa", store.resolve("20260913-100000-blue-left-aaaa").id());
        assertEquals("20260913-100200-blue-left-cccc", store.resolve("blue-left").id());
        assertEquals("20260913-100000-blue-left-aaaa", store.resolve("aaaa").id());
    }

    @Test
    void explainsAmbiguousAndMissingReferences() {
        IllegalArgumentException ambiguous = assertThrows(IllegalArgumentException.class,
                () -> store.resolve("20260913"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> store.resolve("zzzz"));
        IllegalArgumentException outOfRange = assertThrows(IllegalArgumentException.class, () -> store.resolve("#9"));

        assertTrue(ambiguous.getMessage().startsWith("'20260913' matches 3 runs"), ambiguous.getMessage());
        assertTrue(missing.getMessage().startsWith("No run matches 'zzzz'. Use a run ID, a routine name, #number or "
                + "'latest'."), missing.getMessage());
        assertEquals("There is no run #9; the store has 3 run(s)", outOfRange.getMessage());
    }

    @Test
    void reportsUnreadableFilesWithoutFailingTheScan() throws IOException {
        Files.writeString(dir.resolve("runs/corrupt.run.json"), "{not json");
        Files.writeString(dir.resolve("runs/notes.txt"), "ignored");

        RunStore.Scan scan = store.scan();

        assertEquals(3, scan.runs().size());
        assertEquals(1, scan.failures().size());
        assertTrue(scan.failures().keySet().iterator().next().getName().equals("corrupt.run.json"));
    }

    @Test
    void emptyStore() {
        RunStore empty = new RunStore(dir.resolve("nothing").toFile());

        assertTrue(empty.runs().isEmpty());
        assertEquals("No runs found in " + empty.directory().getPath(),
                assertThrows(IllegalArgumentException.class, () -> empty.resolve("latest")).getMessage());
    }

    @Test
    void saveQuietlyCapturesErrors() throws IOException {
        File blocker = dir.resolve("blocker").toFile();
        Files.writeString(blocker.toPath(), "a file where a directory should be");
        RunStore broken = new RunStore(new File(blocker, "runs"));

        assertFalse(broken.saveQuietly(run("r", "blue-left", 0)));
        assertTrue(broken.lastSaveError() instanceof IOException);
    }

    @Test
    void recorderSavesOnStop() {
        RunStore target = new RunStore(dir.resolve("robot").toFile());
        RunRecorder brex = Brex.builder("blue-left").clock(new ManualClock()).random(new Random(1)).saveTo(target)
                .start();

        Run run = brex.complete();

        assertEquals(run.id(), target.resolve("latest").id());
    }
}
