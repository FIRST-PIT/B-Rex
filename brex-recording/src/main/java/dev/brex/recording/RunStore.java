package dev.brex.recording;

import dev.brex.core.format.RunFormat;
import dev.brex.core.format.RunReader;
import dev.brex.core.format.RunWriter;
import dev.brex.core.run.Run;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A directory of run files, one file per run named {@code <run id>.run.json}.
 *
 * <p>On the robot, runs are saved to {@link #ROBOT_DIRECTORY}. On a laptop, a B-rex project keeps
 * them in {@code .brex/runs}.
 */
public final class RunStore {

    /** Where runs are saved on a REV Control Hub or robot controller phone. */
    public static final File ROBOT_DIRECTORY = new File("/sdcard/FIRST/brex/runs");

    private static final Comparator<Run> CHRONOLOGICAL = new Comparator<Run>() {
        @Override
        public int compare(Run a, Run b) {
            int byTime = Long.compare(a.metadata().startedAtEpochMillis(), b.metadata().startedAtEpochMillis());
            return byTime != 0 ? byTime : a.id().compareTo(b.id());
        }
    };

    /** The result of reading every run file in the store. */
    public static final class Scan {
        private final List<Run> runs;
        private final Map<File, String> failures;

        Scan(List<Run> runs, Map<File, String> failures) {
            this.runs = Collections.unmodifiableList(runs);
            this.failures = Collections.unmodifiableMap(failures);
        }

        /** Readable runs, oldest first. */
        public List<Run> runs() {
            return runs;
        }

        /** Files that could not be read, with the reason. */
        public Map<File, String> failures() {
            return failures;
        }
    }

    private final File directory;
    private volatile Exception lastSaveError;

    public RunStore(File directory) {
        this.directory = directory;
    }

    /** The store used on the robot controller. */
    public static RunStore onRobot() {
        return new RunStore(ROBOT_DIRECTORY);
    }

    public File directory() {
        return directory;
    }

    /** The file a run is (or would be) saved to. */
    public File fileFor(Run run) {
        return new File(directory, run.id() + RunFormat.EXTENSION);
    }

    /** Saves a run, replacing any previous file with the same run ID. */
    public File save(Run run) throws IOException {
        File file = fileFor(run);
        RunWriter.write(run, file);
        return file;
    }

    /**
     * Saves a run without throwing. Intended for robot code, where a full storage card must not
     * crash the OpMode. Check {@link #lastSaveError()} to surface failures in telemetry.
     */
    public boolean saveQuietly(Run run) {
        try {
            save(run);
            lastSaveError = null;
            return true;
        } catch (IOException | RuntimeException e) {
            lastSaveError = e;
            return false;
        }
    }

    /** The error from the most recent failed {@link #saveQuietly}, or null. */
    public Exception lastSaveError() {
        return lastSaveError;
    }

    /** Run files in the store, sorted by name (and therefore by start time). */
    public List<File> files() {
        File[] listed = directory.listFiles();
        if (listed == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>();
        for (File file : listed) {
            String name = file.getName();
            if (file.isFile() && (name.endsWith(RunFormat.EXTENSION) || name.endsWith(RunFormat.COMPRESSED_EXTENSION))) {
                files.add(file);
            }
        }
        Collections.sort(files);
        return files;
    }

    /** Reads every run file. Unreadable files are reported, not thrown. */
    public Scan scan() {
        List<Run> runs = new ArrayList<>();
        Map<File, String> failures = new LinkedHashMap<>();
        for (File file : files()) {
            try {
                runs.add(RunReader.read(file));
            } catch (IOException | RuntimeException e) {
                failures.put(file, e.getMessage());
            }
        }
        Collections.sort(runs, CHRONOLOGICAL);
        return new Scan(runs, failures);
    }

    /** Readable runs, oldest first. */
    public List<Run> runs() {
        return scan().runs();
    }

    /** Runs of one routine, oldest first. */
    public List<Run> runsOf(String name) {
        List<Run> result = new ArrayList<>();
        for (Run run : runs()) {
            if (run.name().equals(name)) {
                result.add(run);
            }
        }
        return result;
    }

    /** The most recent run of a routine, or null. */
    public Run latest(String name) {
        List<Run> runs = runsOf(name);
        return runs.isEmpty() ? null : runs.get(runs.size() - 1);
    }

    /** The 1-based chronological position of a run in the store, or -1. */
    public int ordinal(Run run) {
        List<Run> runs = runs();
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i).id().equals(run.id())) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Finds a run by reference. Accepted forms, tried in order:
     *
     * <ul>
     *   <li>{@code latest}: the most recent run of any routine</li>
     *   <li>{@code #41} or {@code 41}: the 41st run, counting from the oldest</li>
     *   <li>an exact run ID</li>
     *   <li>a routine name such as {@code blue-left} or {@code match-038}: its latest run</li>
     *   <li>any unique fragment of a run ID, such as {@code 3fa9}</li>
     * </ul>
     *
     * @throws IllegalArgumentException with a helpful message when nothing or several runs match
     */
    public Run resolve(String reference) {
        List<Run> runs = runs();
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("No runs found in " + directory.getPath());
        }
        String ref = reference.trim();
        if (ref.equalsIgnoreCase("latest")) {
            return runs.get(runs.size() - 1);
        }
        String digits = ref.startsWith("#") ? ref.substring(1) : ref;
        if (!digits.isEmpty() && digits.matches("\\d+") && digits.length() <= 6) {
            int ordinal = Integer.parseInt(digits);
            if (ordinal >= 1 && ordinal <= runs.size()) {
                return runs.get(ordinal - 1);
            }
            if (ref.startsWith("#")) {
                throw new IllegalArgumentException("There is no run " + ref + "; the store has " + runs.size()
                        + " run(s)");
            }
        }
        Run latestOfRoutine = null;
        for (Run run : runs) {
            if (run.id().equals(ref)) {
                return run;
            }
            if (run.name().equals(ref)) {
                latestOfRoutine = run;
            }
        }
        if (latestOfRoutine != null) {
            return latestOfRoutine;
        }
        List<Run> partial = new ArrayList<>();
        String lower = ref.toLowerCase(Locale.ROOT);
        for (Run run : runs) {
            if (run.id().toLowerCase(Locale.ROOT).contains(lower)) {
                partial.add(run);
            }
        }
        if (partial.size() == 1) {
            return partial.get(0);
        }
        if (partial.size() > 1) {
            throw new IllegalArgumentException("'" + ref + "' matches " + partial.size() + " runs: "
                    + ids(partial.subList(Math.max(0, partial.size() - 5), partial.size()))
                    + ". Use a longer part of the run ID.");
        }
        throw new IllegalArgumentException("No run matches '" + ref + "'. Use a run ID, a routine name, #number or "
                + "'latest'. Recent runs: " + ids(runs.subList(Math.max(0, runs.size() - 3), runs.size())));
    }

    private static String ids(List<Run> runs) {
        List<String> ids = new ArrayList<>();
        for (Run run : runs) {
            ids.add(run.id());
        }
        return Arrays.toString(ids.toArray()).replace("[", "").replace("]", "");
    }
}
