package dev.brex.core.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.event.Event;
import dev.brex.core.event.LogEntry;
import dev.brex.core.event.LogLevel;
import dev.brex.core.event.MechanismStateChange;
import dev.brex.core.geometry.Pose;
import dev.brex.core.geometry.Velocity;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunFormatTest {

    static Run fullRun() {
        RunMetadata metadata = RunMetadata.builder("blue-left")
                .kind(RunKind.MATCH)
                .source(RunSource.SIMULATION)
                .startedAtEpochMillis(1_789_320_612_345L)
                .robot("Rex")
                .robotConfig("drivetrain", "mecanum")
                .softwareVersion("2.3.1")
                .gitCommit("a91f42c")
                .gitBranch("main")
                .gitDirty(true)
                .tag("qualifier")
                .property("alliance", "blue")
                .build();
        return Run.builder("20260913-175012-blue-left-3fa9", metadata)
                .status(RunStatus.FAILED)
                .duration(22.31)
                .startPose(Pose.ofInches(-36, 63, -90))
                .trajectory(new Trajectory.Builder()
                        .add(0, Pose.of(0.1, 0.2, 0.3), Velocity.of(0, 0, 0))
                        .add(0.02, Pose.of(0.1 + 1e-7, 0.2, 0.3), Velocity.of(0.1, Double.NaN, 0))
                        .build())
                .telemetry(new Telemetry(List.of(
                        TelemetryChannel.numeric("lift.position", new double[] {0, 0.5}, new double[] {0, 1234.5}),
                        TelemetryChannel.booleans("intake.hasSample", new double[] {1}, new boolean[] {true}),
                        TelemetryChannel.text("auto.state", new double[] {0, 2}, new String[] {"DRIVE", "say \"hi\""}))))
                .event(Event.of(3.2, "alignment.complete"))
                .event(Event.of(9.1, "deposit.complete", Map.of("points", "8")))
                .mechanismState(MechanismStateChange.of(7, "lift", "RAISING"))
                .log(LogEntry.of(4, LogLevel.ERROR, "lift stalled"))
                .phase(MatchPhase.of("autonomous", 0, 22.31))
                .testResult(TestResult.failed("regression.duration", "+0.84s slower"))
                .build();
    }

    @Test
    void roundTripsEveryField() {
        Run original = fullRun();

        Run copy = RunReader.fromJson(RunWriter.toJson(original), "test");

        assertEquals(original.id(), copy.id());
        RunMetadata m = copy.metadata();
        assertEquals(RunKind.MATCH, m.kind());
        assertEquals(RunSource.SIMULATION, m.source());
        assertEquals(1_789_320_612_345L, m.startedAtEpochMillis());
        assertEquals("Rex", m.robot());
        assertEquals(Map.of("drivetrain", "mecanum"), m.robotConfig());
        assertEquals("2.3.1", m.softwareVersion());
        assertEquals("a91f42c", m.gitCommit());
        assertEquals("main", m.gitBranch());
        assertEquals(Boolean.TRUE, m.gitDirty());
        assertEquals(List.of("qualifier"), m.tags());
        assertEquals(Map.of("alliance", "blue"), m.properties());
        assertEquals(RunStatus.FAILED, copy.status());
        assertEquals(22.31, copy.duration());
        assertEquals(original.startPose(), copy.startPose());
        assertEquals(original.endPose(), copy.endPose());
        assertArrayEquals(original.trajectory().xs(), copy.trajectory().xs());
        assertArrayEquals(original.trajectory().recordedVy(), copy.trajectory().recordedVy());
        assertEquals(original.events(), copy.events());
        assertEquals(original.mechanismStates(), copy.mechanismStates());
        assertEquals(original.log(), copy.log());
        assertEquals(original.phases(), copy.phases());
        assertEquals(original.testResults(), copy.testResults());
        assertEquals(ChannelType.BOOLEAN, copy.telemetry().require("intake.hasSample").type());
        assertEquals("say \"hi\"", copy.telemetry().require("auto.state").text(1));
        assertArrayEquals(new double[] {0, 1234.5}, copy.telemetry().require("lift.position").numbers());
    }

    @Test
    void serializationIsDeterministic() {
        assertEquals(RunWriter.toJson(fullRun()), RunWriter.toJson(RunReader.fromJson(RunWriter.toJson(fullRun()), "t")));
    }

    @Test
    void minimalRunOmitsOptionalFields() {
        Run run = Run.builder("r1", RunMetadata.builder("blue-left").build()).duration(1).build();

        String json = RunWriter.toJson(run);
        Run copy = RunReader.fromJson(json, "t");

        assertFalse(json.contains("\"git\""));
        assertFalse(json.contains("\"vx\""));
        assertNull(copy.metadata().gitCommit());
        assertNull(copy.metadata().gitDirty());
        assertNull(copy.startPose());
        assertTrue(copy.trajectory().isEmpty());
    }

    @Test
    void writesAtomicallyAndSupportsGzip(@TempDir Path dir) throws IOException {
        File plain = dir.resolve("runs/a.run.json").toFile();
        File compressed = dir.resolve("runs/a.run.json.gz").toFile();

        RunWriter.write(fullRun(), plain);
        RunWriter.write(fullRun(), compressed);

        assertEquals(fullRun().id(), RunReader.read(plain).id());
        assertEquals(fullRun().id(), RunReader.read(compressed).id());
        byte[] head = Files.readAllBytes(compressed.toPath());
        assertEquals(0x1f, head[0] & 0xff);
        assertFalse(new File(plain.getPath() + ".tmp").exists());
    }

    @Test
    void ignoresUnknownFields() {
        String json = RunWriter.toJson(fullRun()).replaceFirst("\\{", "{\"futureField\": {\"x\": 1},");

        assertEquals(fullRun().id(), RunReader.fromJson(json, "t").id());
    }

    @Test
    void rejectsNewerFormatVersionWithUpgradeAdvice() {
        String json = RunWriter.toJson(fullRun()).replace("\"formatVersion\": 1", "\"formatVersion\": 7");

        RunFormatException error = assertThrows(RunFormatException.class, () -> RunReader.fromJson(json, "x.run.json"));

        assertEquals("x.run.json uses run format version 7 but this B-rex only reads up to version 1. "
                + "Upgrade B-rex to read it.", error.getMessage());
    }

    @Test
    void rejectsNonRunDocuments() {
        RunFormatException error = assertThrows(RunFormatException.class,
                () -> RunReader.fromJson("{\"hello\": 1}", "package.json"));

        assertEquals("package.json is not a B-rex run file (expected \"format\": \"brex-run\")", error.getMessage());
    }

    @Test
    void reportsCorruptFieldsWithPath() {
        String json = RunWriter.toJson(fullRun()).replace("\"duration\": 22.31", "\"duration\": \"long\"");

        RunFormatException error = assertThrows(RunFormatException.class, () -> RunReader.fromJson(json, "x.run.json"));

        assertEquals("x.run.json: $.duration must be a number", error.getMessage());
    }

    @Test
    void reportsTruncatedFiles() {
        String json = RunWriter.toJson(fullRun());

        RunFormatException error = assertThrows(RunFormatException.class,
                () -> RunReader.fromJson(json.substring(0, json.length() / 2), "x.run.json"));

        assertTrue(error.getMessage().startsWith("x.run.json is not a valid run file: Invalid JSON at line"),
                error.getMessage());
    }

    @Test
    void readsFromStream() throws IOException {
        byte[] bytes = RunWriter.toJson(fullRun()).getBytes(StandardCharsets.UTF_8);

        assertEquals(fullRun().id(), RunReader.read(new ByteArrayInputStream(bytes), "stream").id());
    }

    /** A checked-in v1 document guards against accidental format changes. */
    @Test
    void readsReferenceVersion1Document() {
        String v1 = """
                {
                  "format": "brex-run",
                  "formatVersion": 1,
                  "id": "20260101-120000-red-right-0001",
                  "metadata": {"name": "red-right", "startedAt": 1767268800000},
                  "status": "COMPLETED",
                  "duration": 1.5,
                  "trajectory": {"t": [0, 1.5], "x": [0, 1], "y": [0, 0], "heading": [0, 0]},
                  "telemetry": {"lift.position": {"type": "number", "t": [0], "v": [5]}},
                  "events": [{"t": 1.0, "name": "park"}],
                  "mechanismStates": [],
                  "log": [],
                  "phases": [],
                  "testResults": []
                }
                """;

        Run run = RunReader.fromJson(v1, "v1");

        assertEquals("red-right", run.name());
        assertEquals(RunKind.AUTONOMOUS, run.metadata().kind());
        assertEquals(Pose.of(1, 0, 0), run.endPose());
        assertTrue(run.hasEvent("park"));
    }
}
