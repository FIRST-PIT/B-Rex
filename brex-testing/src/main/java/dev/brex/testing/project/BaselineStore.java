package dev.brex.testing.project;

import dev.brex.core.format.RunFormat;
import dev.brex.core.format.RunReader;
import dev.brex.core.format.RunWriter;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunIds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Known-good runs, one per routine, stored as {@code <routine>.run.json}. Baselines are meant to be
 * committed to Git so the whole team (and CI) tests against the same reference.
 */
public final class BaselineStore {

    private final Path directory;

    public BaselineStore(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public Path fileFor(String routine) {
        return directory.resolve(RunIds.slug(routine) + RunFormat.EXTENSION);
    }

    /** Saves {@code run} as the baseline for its routine, replacing any previous baseline. */
    public Path save(Run run) throws IOException {
        Path file = fileFor(run.name());
        RunWriter.write(run, file.toFile());
        return file;
    }

    public Optional<Run> load(String routine) throws IOException {
        Path file = fileFor(routine);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(RunReader.read(file.toFile()));
    }

    /** Routine names that have a baseline, sorted. */
    public List<String> routines() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(RunFormat.EXTENSION))
                    .map(name -> name.substring(0, name.length() - RunFormat.EXTENSION.length()))
                    .sorted()
                    .toList();
        }
    }

    public boolean delete(String routine) throws IOException {
        return Files.deleteIfExists(fileFor(routine));
    }
}
