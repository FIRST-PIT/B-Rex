package dev.brex.core.format;

import dev.brex.core.BrexVersion;
import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.json.JsonWriter;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.TestResult;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Serializes runs to the current version of the B-rex run format. */
public final class RunWriter {

    private RunWriter() {
    }

    /** Serializes a run to pretty-printed JSON. */
    public static String toJson(Run run) {
        StringBuilder sb = new StringBuilder();
        write(run, sb);
        return sb.toString();
    }

    public static void write(Run run, Appendable out) {
        JsonWriter w = new JsonWriter(out, true);
        w.beginObject();
        w.name("format").value(RunFormat.FORMAT);
        w.name("formatVersion").value(RunFormat.CURRENT_VERSION);
        w.name("writer").value("brex " + BrexVersion.get());
        w.name("id").value(run.id());
        writeMetadata(w, run.metadata());
        w.name("status").value(run.status().name());
        w.name("duration").value(run.duration());
        if (run.startPose() != null) {
            w.name("startPose");
            writePose(w, run.startPose());
        }
        if (run.endPose() != null) {
            w.name("endPose");
            writePose(w, run.endPose());
        }
        writeTrajectory(w, run.trajectory());
        writeTelemetry(w, run);
        writeEvents(w, run.events());
        writeMechanismStates(w, run.mechanismStates());
        writeLog(w, run.log());
        writePhases(w, run.phases());
        writeTestResults(w, run.testResults());
        w.endObject();
        try {
            out.append('\n');
        } catch (IOException e) {
            throw new RunFormatException("Could not write run " + run.id(), e);
        }
    }

    /**
     * Writes a run to a file atomically (via a temporary file and rename), so a crash while saving
     * never leaves a truncated run behind. Files ending in {@code .gz} are gzip-compressed.
     */
    public static void write(Run run, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }
        File temp = new File(file.getPath() + ".tmp");
        OutputStream stream = new FileOutputStream(temp);
        try {
            if (file.getName().endsWith(".gz")) {
                stream = new GZIPOutputStream(stream);
            }
            Writer writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"), 1 << 16);
            write(run, writer);
            writer.flush();
            writer.close();
        } catch (IOException | RuntimeException e) {
            stream.close();
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
            throw e;
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not replace existing file " + file);
        }
        if (!temp.renameTo(file)) {
            throw new IOException("Could not rename " + temp + " to " + file);
        }
    }

    private static void writeMetadata(JsonWriter w, RunMetadata m) {
        w.name("metadata").beginObject();
        w.name("name").value(m.name());
        w.name("kind").value(m.kind().name());
        w.name("source").value(m.source().name());
        w.name("startedAt").value(m.startedAtEpochMillis());
        optional(w, "robot", m.robot());
        if (!m.robotConfig().isEmpty()) {
            w.name("robotConfig");
            writeStringMap(w, m.robotConfig());
        }
        optional(w, "softwareVersion", m.softwareVersion());
        if (m.gitCommit() != null || m.gitBranch() != null || m.gitDirty() != null) {
            w.name("git").beginObject();
            optional(w, "commit", m.gitCommit());
            optional(w, "branch", m.gitBranch());
            if (m.gitDirty() != null) {
                w.name("dirty").value(m.gitDirty());
            }
            w.endObject();
        }
        if (!m.tags().isEmpty()) {
            w.name("tags").beginArray();
            for (String tag : m.tags()) {
                w.value(tag);
            }
            w.endArray();
        }
        if (!m.properties().isEmpty()) {
            w.name("properties");
            writeStringMap(w, m.properties());
        }
        w.endObject();
    }

    private static void writePose(JsonWriter w, Pose pose) {
        w.beginObject();
        w.name("x").value(pose.x());
        w.name("y").value(pose.y());
        w.name("heading").value(pose.heading());
        w.endObject();
    }

    private static void writeTrajectory(JsonWriter w, Trajectory trajectory) {
        w.name("trajectory").beginObject();
        array(w, "t", trajectory.times());
        array(w, "x", trajectory.xs());
        array(w, "y", trajectory.ys());
        array(w, "heading", trajectory.headings());
        if (trajectory.hasRecordedVelocity()) {
            array(w, "vx", trajectory.recordedVx());
            array(w, "vy", trajectory.recordedVy());
            array(w, "omega", trajectory.recordedOmega());
        }
        w.endObject();
    }

    private static void writeTelemetry(JsonWriter w, Run run) {
        w.name("telemetry").beginObject();
        for (TelemetryChannel channel : run.telemetry().channels()) {
            w.name(channel.key()).beginObject();
            w.name("type").value(channel.type().id());
            array(w, "t", channel.times());
            w.name("v").beginArray();
            for (int i = 0; i < channel.size(); i++) {
                if (channel.type() == ChannelType.TEXT) {
                    w.value(channel.text(i));
                } else {
                    w.value(channel.number(i));
                }
            }
            w.endArray();
            w.endObject();
        }
        w.endObject();
    }

    private static void writeEvents(JsonWriter w, List<Event> events) {
        w.name("events").beginArray();
        for (Event event : events) {
            w.beginObject();
            w.name("t").value(event.time());
            w.name("name").value(event.name());
            if (!event.attributes().isEmpty()) {
                w.name("attributes");
                writeStringMap(w, event.attributes());
            }
            w.endObject();
        }
        w.endArray();
    }

    private static void writeMechanismStates(JsonWriter w, List<MechanismStateChange> changes) {
        w.name("mechanismStates").beginArray();
        for (MechanismStateChange change : changes) {
            w.beginObject();
            w.name("t").value(change.time());
            w.name("mechanism").value(change.mechanism());
            w.name("state").value(change.state());
            w.endObject();
        }
        w.endArray();
    }

    private static void writeLog(JsonWriter w, List<LogEntry> log) {
        w.name("log").beginArray();
        for (LogEntry entry : log) {
            w.beginObject();
            w.name("t").value(entry.time());
            w.name("level").value(entry.level().name());
            w.name("message").value(entry.message());
            w.endObject();
        }
        w.endArray();
    }

    private static void writePhases(JsonWriter w, List<MatchPhase> phases) {
        w.name("phases").beginArray();
        for (MatchPhase phase : phases) {
            w.beginObject();
            w.name("name").value(phase.name());
            w.name("start").value(phase.start());
            w.name("end").value(phase.end());
            w.endObject();
        }
        w.endArray();
    }

    private static void writeTestResults(JsonWriter w, List<TestResult> results) {
        w.name("testResults").beginArray();
        for (TestResult result : results) {
            w.beginObject();
            w.name("name").value(result.name());
            w.name("status").value(result.status().name());
            if (!result.message().isEmpty()) {
                w.name("message").value(result.message());
            }
            w.endObject();
        }
        w.endArray();
    }

    private static void writeStringMap(JsonWriter w, Map<String, String> map) {
        w.beginObject();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            w.name(entry.getKey()).value(entry.getValue());
        }
        w.endObject();
    }

    private static void array(JsonWriter w, String name, double[] values) {
        w.name(name).beginArray();
        for (double value : values) {
            w.value(value);
        }
        w.endArray();
    }

    private static void optional(JsonWriter w, String name, String value) {
        if (value != null) {
            w.name(name).value(value);
        }
    }
}
