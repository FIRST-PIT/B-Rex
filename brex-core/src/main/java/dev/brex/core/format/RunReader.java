package dev.brex.core.format;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.json.Json;
import dev.brex.core.json.JsonException;
import dev.brex.core.json.JsonObject;
import dev.brex.core.run.MatchPhase;
import dev.brex.core.run.Run;
import dev.brex.core.run.RunKind;
import dev.brex.core.run.RunMetadata;
import dev.brex.core.run.RunSource;
import dev.brex.core.run.RunStatus;
import dev.brex.core.run.TestResult;
import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import dev.brex.core.trajectory.Trajectory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Reads run files of any supported format version. */
public final class RunReader {

    private RunReader() {
    }

    /** Reads a run file; gzip compression is detected automatically. */
    public static Run read(File file) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            return read(in, file.getPath());
        } finally {
            in.close();
        }
    }

    /** Reads a run from a stream; gzip compression is detected automatically. */
    public static Run read(InputStream input, String source) throws IOException {
        InputStream in = input.markSupported() ? input : new BufferedInputStream(input);
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            in = new GZIPInputStream(in);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1 << 16];
        int n;
        while ((n = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, n);
        }
        return fromJson(bytes.toString("UTF-8"), source);
    }

    /**
     * Parses a run document.
     *
     * @param source a file name or other description used in error messages
     */
    public static Run fromJson(String json, String source) {
        JsonObject root;
        try {
            root = Json.parseObject(json);
        } catch (JsonException e) {
            throw new RunFormatException(source + " is not a valid run file: " + e.getMessage(), e);
        }
        String format = root.optString("format", null);
        if (!RunFormat.FORMAT.equals(format)) {
            throw new RunFormatException(source + " is not a B-rex run file (expected \"format\": \""
                    + RunFormat.FORMAT + "\")");
        }
        int version;
        try {
            version = root.getInt("formatVersion");
        } catch (JsonException e) {
            throw new RunFormatException(source + ": " + e.getMessage(), e);
        }
        if (version > RunFormat.CURRENT_VERSION) {
            throw new RunFormatException(source + " uses run format version " + version + " but this B-rex only "
                    + "reads up to version " + RunFormat.CURRENT_VERSION + ". Upgrade B-rex to read it.");
        }
        if (version < 1) {
            throw new RunFormatException(source + " has invalid run format version " + version);
        }
        // Version 1 is the first format. When version 2 exists, upgrade older documents here, one
        // version at a time, before parsing.
        try {
            return parseV1(root);
        } catch (JsonException | IllegalArgumentException e) {
            throw new RunFormatException(source + ": " + e.getMessage(), e);
        }
    }

    private static Run parseV1(JsonObject root) {
        RunMetadata metadata = parseMetadata(root.getObject("metadata"));
        Run.Builder builder = Run.builder(root.getString("id"), metadata)
                .status(enumValue(RunStatus.class, root.getString("status"), "status"))
                .duration(root.getDouble("duration"))
                .trajectory(parseTrajectory(root.optObject("trajectory")))
                .telemetry(parseTelemetry(root.optObject("telemetry")));
        if (root.has("startPose")) {
            builder.startPose(parsePose(root.getObject("startPose")));
        }
        if (root.has("endPose")) {
            builder.endPose(parsePose(root.getObject("endPose")));
        }
        for (JsonObject event : root.optObjectList("events")) {
            builder.event(Event.of(event.getDouble("t"), event.getString("name"), event.getStringMap("attributes")));
        }
        for (JsonObject change : root.optObjectList("mechanismStates")) {
            builder.mechanismState(MechanismStateChange.of(change.getDouble("t"), change.getString("mechanism"),
                    change.getString("state")));
        }
        for (JsonObject entry : root.optObjectList("log")) {
            builder.log(LogEntry.of(entry.getDouble("t"), enumValue(LogLevel.class, entry.getString("level"), "level"),
                    entry.optString("message", "")));
        }
        for (JsonObject phase : root.optObjectList("phases")) {
            builder.phase(MatchPhase.of(phase.getString("name"), phase.getDouble("start"), phase.getDouble("end")));
        }
        for (JsonObject result : root.optObjectList("testResults")) {
            builder.testResult(TestResult.of(result.getString("name"),
                    enumValue(TestResult.Status.class, result.getString("status"), "test result status"),
                    result.optString("message", "")));
        }
        return builder.build();
    }

    private static RunMetadata parseMetadata(JsonObject m) {
        RunMetadata.Builder b = RunMetadata.builder(m.getString("name"))
                .kind(enumValue(RunKind.class, m.optString("kind", RunKind.AUTONOMOUS.name()), "kind"))
                .source(enumValue(RunSource.class, m.optString("source", RunSource.ROBOT.name()), "source"))
                .startedAtEpochMillis(m.optLong("startedAt", 0))
                .robot(m.optString("robot", null))
                .robotConfig(m.getStringMap("robotConfig"))
                .softwareVersion(m.optString("softwareVersion", null))
                .properties(m.getStringMap("properties"));
        if (m.has("git")) {
            JsonObject git = m.getObject("git");
            b.gitCommit(git.optString("commit", null)).gitBranch(git.optString("branch", null));
            if (git.has("dirty")) {
                b.gitDirty(git.getBoolean("dirty"));
            }
        }
        if (m.has("tags")) {
            b.tags(m.getStringList("tags"));
        }
        return b.build();
    }

    private static Pose parsePose(JsonObject pose) {
        return Pose.of(pose.getDouble("x"), pose.getDouble("y"), pose.getDouble("heading"));
    }

    private static Trajectory parseTrajectory(JsonObject t) {
        if (!t.has("t")) {
            return Trajectory.empty();
        }
        return Trajectory.fromColumns(t.getDoubleArray("t"), t.getDoubleArray("x"), t.getDoubleArray("y"),
                t.getDoubleArray("heading"), t.has("vx") ? t.getDoubleArray("vx") : null,
                t.has("vy") ? t.getDoubleArray("vy") : null, t.has("omega") ? t.getDoubleArray("omega") : null);
    }

    private static Telemetry parseTelemetry(JsonObject telemetry) {
        List<TelemetryChannel> channels = new ArrayList<>();
        for (String key : telemetry.keys()) {
            JsonObject channel = telemetry.getObject(key);
            ChannelType type = ChannelType.fromId(channel.getString("type"));
            double[] times = channel.getDoubleArray("t");
            switch (type) {
                case TEXT:
                    channels.add(TelemetryChannel.text(key, times, channel.getStringList("v").toArray(new String[0])));
                    break;
                case BOOLEAN:
                    double[] raw = channel.getDoubleArray("v");
                    boolean[] values = new boolean[raw.length];
                    for (int i = 0; i < raw.length; i++) {
                        values[i] = raw[i] != 0;
                    }
                    channels.add(TelemetryChannel.booleans(key, times, values));
                    break;
                default:
                    channels.add(TelemetryChannel.numeric(key, times, channel.getDoubleArray("v")));
            }
        }
        return new Telemetry(channels);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown " + field + " '" + value + "'");
        }
    }
}
