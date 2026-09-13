package dev.brex.recording;

import dev.brex.core.telemetry.ChannelType;
import dev.brex.core.telemetry.Telemetry;
import dev.brex.core.telemetry.TelemetryChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records arbitrary telemetry keys during a run.
 *
 * <pre>{@code
 * brex.telemetry.record("lift.position", lift.getCurrentPosition());
 * brex.telemetry.record("intake.hasSample", colorSensor.detected());
 * brex.telemetry.record("auto.state", state.name());
 * }</pre>
 *
 * <p>A key's type is fixed by its first sample. Invalid keys and type mismatches never throw;
 * the sample is dropped and a single warning is added to the run.
 */
public final class TelemetryRecorder {

    private final RunRecorder recorder;
    private final Map<String, TelemetryChannel.Builder> channels = new LinkedHashMap<>();

    TelemetryRecorder(RunRecorder recorder) {
        this.recorder = recorder;
    }

    public void record(String key, double value) {
        synchronized (recorder) {
            TelemetryChannel.Builder channel = channel(key, ChannelType.NUMBER);
            if (channel != null) {
                channel.addNumber(recorder.now(), value);
            }
        }
    }

    public void record(String key, boolean value) {
        synchronized (recorder) {
            TelemetryChannel.Builder channel = channel(key, ChannelType.BOOLEAN);
            if (channel != null) {
                channel.addNumber(recorder.now(), value ? 1 : 0);
            }
        }
    }

    public void record(String key, String value) {
        synchronized (recorder) {
            TelemetryChannel.Builder channel = channel(key, ChannelType.TEXT);
            if (channel != null) {
                channel.addText(recorder.now(), value == null ? "null" : value);
            }
        }
    }

    /** Records an enum constant's name, a convenient way to log state machines. */
    public void record(String key, Enum<?> value) {
        record(key, value == null ? null : value.name());
    }

    private TelemetryChannel.Builder channel(String key, ChannelType type) {
        if (!recorder.acceptingSamples()) {
            return null;
        }
        TelemetryChannel.Builder channel = channels.get(key);
        if (channel == null) {
            try {
                TelemetryChannel.requireValidKey(key);
            } catch (IllegalArgumentException e) {
                recorder.internalWarning(e.getMessage() + "; samples for this key are dropped");
                return null;
            }
            channel = new TelemetryChannel.Builder(key, type);
            channels.put(key, channel);
        }
        if (channel.type() != type) {
            recorder.internalWarning("Telemetry key '" + key + "' was first recorded as " + channel.type().id()
                    + " but later as " + type.id() + "; mismatched samples are dropped");
            return null;
        }
        if (channel.size() >= recorder.maxSamplesPerChannel()) {
            recorder.internalWarning("Telemetry key '" + key + "' reached " + recorder.maxSamplesPerChannel()
                    + " samples; later samples are dropped");
            return null;
        }
        return channel;
    }

    Telemetry build() {
        List<TelemetryChannel> built = new ArrayList<>(channels.size());
        for (TelemetryChannel.Builder channel : channels.values()) {
            built.add(channel.build());
        }
        return new Telemetry(built);
    }
}
