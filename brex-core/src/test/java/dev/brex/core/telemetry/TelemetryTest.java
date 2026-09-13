package dev.brex.core.telemetry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryTest {

    @Test
    void builderRecordsNumericSamples() {
        TelemetryChannel.Builder builder = new TelemetryChannel.Builder("lift.position", ChannelType.NUMBER);
        builder.addNumber(0.0, 10);
        builder.addNumber(0.5, 20);

        TelemetryChannel channel = builder.build();

        assertEquals(2, channel.size());
        assertArrayEquals(new double[] {0.0, 0.5}, channel.times());
        assertArrayEquals(new double[] {10, 20}, channel.numbers());
    }

    @Test
    void valuesAreSampleAndHold() {
        TelemetryChannel channel = TelemetryChannel.numeric("lift.position", new double[] {1, 2, 3},
                new double[] {100, 200, 300});

        assertTrue(Double.isNaN(channel.numberAt(0.5)));
        assertEquals(100, channel.numberAt(1.0));
        assertEquals(200, channel.numberAt(2.9));
        assertEquals(300, channel.numberAt(99));
    }

    @Test
    void textChannelsHoldStrings() {
        TelemetryChannel channel = TelemetryChannel.text("auto.state", new double[] {0, 1},
                new String[] {"DRIVE", "INTAKE"});

        assertEquals("INTAKE", channel.textAt(1.5));
        assertNull(channel.textAt(-1));
        assertThrows(IllegalStateException.class, () -> channel.number(0));
    }

    @Test
    void booleanChannelsStoreZeroOrOne() {
        TelemetryChannel channel = TelemetryChannel.booleans("intake.hasSample", new double[] {0, 1},
                new boolean[] {false, true});

        assertFalse(channel.bool(0));
        assertTrue(channel.bool(1));
        assertEquals("true", channel.text(1));
    }

    @Test
    void builderClampsBackwardsTimestamps() {
        TelemetryChannel.Builder builder = new TelemetryChannel.Builder("x", ChannelType.NUMBER);
        builder.addNumber(1.0, 1);
        builder.addNumber(0.9, 2);

        assertArrayEquals(new double[] {1.0, 1.0}, builder.build().times());
    }

    @Test
    void rejectsUnsortedTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> TelemetryChannel.numeric("x", new double[] {1, 0}, new double[] {1, 2}));
    }

    @Test
    void rejectsKeysWithWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryChannel.Builder("lift position",
                ChannelType.NUMBER));
    }

    @Test
    void slicesByTime() {
        TelemetryChannel channel = TelemetryChannel.numeric("x", new double[] {0, 1, 2, 3},
                new double[] {0, 10, 20, 30});

        TelemetryChannel slice = channel.slice(0.5, 2.0);

        assertArrayEquals(new double[] {1, 2}, slice.times());
        assertArrayEquals(new double[] {10, 20}, slice.numbers());
    }

    @Test
    void telemetryListsKeysByPrefixInSortedOrder() {
        Telemetry telemetry = new Telemetry(List.of(
                TelemetryChannel.numeric("motor.lift.power", new double[0], new double[0]),
                TelemetryChannel.numeric("intake.velocity", new double[0], new double[0]),
                TelemetryChannel.numeric("motor.arm.power", new double[0], new double[0])));

        assertEquals(List.of("motor.arm.power", "motor.lift.power"), telemetry.keysWithPrefix("motor."));
        assertEquals(List.of("intake.velocity", "motor.arm.power", "motor.lift.power"),
                List.copyOf(telemetry.keys()));
    }

    @Test
    void requireSuggestsSimilarKeys() {
        Telemetry telemetry = new Telemetry(List.of(
                TelemetryChannel.numeric("lift.position", new double[0], new double[0])));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> telemetry.require("lift.pos"));

        assertEquals("No telemetry recorded for 'lift.pos' (recorded keys include: [lift.position])",
                error.getMessage());
    }

    @Test
    void rejectsDuplicateChannels() {
        TelemetryChannel channel = TelemetryChannel.numeric("x", new double[0], new double[0]);

        assertThrows(IllegalArgumentException.class, () -> new Telemetry(List.of(channel, channel)));
    }
}
