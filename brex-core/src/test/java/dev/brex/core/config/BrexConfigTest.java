package dev.brex.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.geometry.DistanceUnit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrexConfigTest {

    private static final double EPS = 1e-12;

    private static BrexConfig config(String text) {
        return BrexConfig.parse(text, "brex.properties");
    }

    @Test
    void emptyConfigReturnsDefaults() {
        BrexConfig config = BrexConfig.empty();

        assertEquals(0.5, config.seconds("regression.maxDurationIncrease", 0.5));
        assertEquals(DistanceUnit.CENTIMETERS, config.distanceUnit());
        assertFalse(config.has("anything"));
    }

    @Test
    void parsesQuantitiesWithUnits() {
        BrexConfig config = config("""
                a = 0.5s
                b = 250ms
                c = 3cm
                d = 1in
                e = 2deg
                f = 0.03rad
                g = 2cm/s
                h = 1.5
                """);

        assertEquals(0.5, config.seconds("a", 0), EPS);
        assertEquals(0.25, config.seconds("b", 0), EPS);
        assertEquals(0.03, config.meters("c", 0), EPS);
        assertEquals(0.0254, config.meters("d", 0), EPS);
        assertEquals(Math.toRadians(2), config.radians("e", 0), EPS);
        assertEquals(0.03, config.radians("f", 0), EPS);
        assertEquals(0.02, config.metersPerSecond("g", 0), EPS);
        assertEquals(1.5, config.seconds("h", 0), EPS);
    }

    @Test
    void distancesWithoutUnitsAreRejectedAsAmbiguous() {
        ConfigException error = assertThrows(ConfigException.class,
                () -> config("regression.maxFinalPositionError = 3").meters("regression.maxFinalPositionError", 0));

        assertEquals("brex.properties: regression.maxFinalPositionError = 3 is ambiguous; add a unit, for example "
                + "3cm or 3in", error.getMessage());
    }

    @Test
    void invalidValuesNameTheKeyAndExpectation() {
        ConfigException error = assertThrows(ConfigException.class,
                () -> config("regression.maxDurationIncrease = fast").seconds("regression.maxDurationIncrease", 0));

        assertEquals("brex.properties: regression.maxDurationIncrease must start with a number, got 'fast'",
                error.getMessage());
        assertThrows(ConfigException.class, () -> config("x = 3kg").meters("x", 0));
        assertThrows(ConfigException.class, () -> config("x = maybe").bool("x", false));
    }

    @Test
    void routineOverridesTakePrecedence() {
        BrexConfig config = config("""
                regression.maxDurationIncrease = 0.5s
                routine.blue-left.regression.maxDurationIncrease = 0.3s
                """);

        assertEquals(0.3, config.forRoutine("blue-left").seconds("regression.maxDurationIncrease", 0), EPS);
        assertEquals(0.5, config.forRoutine("red-right").seconds("regression.maxDurationIncrease", 0), EPS);
        assertEquals(0.5, config.seconds("regression.maxDurationIncrease", 0), EPS);
    }

    @Test
    void routineOverrideErrorsNameTheOverrideKey() {
        BrexConfig config = config("routine.blue-left.scoring.historyWindow = ten").forRoutine("blue-left");

        ConfigException error = assertThrows(ConfigException.class, () -> config.integer("scoring.historyWindow", 1));

        assertEquals("brex.properties: routine.blue-left.scoring.historyWindow must be a whole number, got 'ten'",
                error.getMessage());
    }

    @Test
    void parsesListsAndBooleans() {
        BrexConfig config = config("mechanisms.idleStates = IDLE, STOWED ,,HOME\nregression.requireBaselineEvents=no");

        assertEquals(List.of("IDLE", "STOWED", "HOME"), config.list("mechanisms.idleStates", List.of()));
        assertFalse(config.bool("regression.requireBaselineEvents", true));
    }

    @Test
    void reportsUnknownKeysIncludingRoutineOverrides() {
        BrexConfig config = config("""
                regression.maxDurationIncrease = 0.5s
                regresion.maxEventDelay = 1s
                routine.blue-left.regression.maxEventDelay = 1s
                routine.blue-left.scoring.weight.sped = 2
                """);

        assertEquals(List.of("regresion.maxEventDelay", "routine.blue-left.scoring.weight.sped"),
                config.unknownKeys());
    }

    @Test
    void blankValuesFallBackToDefaults() {
        assertEquals(30.0, config("scoring.targetDuration =   ").seconds("scoring.targetDuration", 30), EPS);
    }

    @Test
    void loadsFromFileAndToleratesMissingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("brex.properties");
        Files.writeString(file, "units.distance = in\n");

        assertEquals(DistanceUnit.INCHES, BrexConfig.load(file.toFile()).distanceUnit());
        assertTrue(BrexConfig.load(new File(dir.toFile(), "missing.properties")).unknownKeys().isEmpty());
    }
}
