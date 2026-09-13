package dev.brex.core.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesNestedDocument() {
        Object parsed = Json.parse("{\"a\": [1, 2.5, -3e2], \"b\": {\"c\": true, \"d\": null}, \"e\": \"x\"}");

        Map<?, ?> root = (Map<?, ?>) parsed;
        assertEquals(List.of(1.0, 2.5, -300.0), root.get("a"));
        Map<?, ?> b = (Map<?, ?>) root.get("b");
        assertEquals(true, b.get("c"));
        assertNull(b.get("d"));
        assertEquals("x", root.get("e"));
    }

    @Test
    void roundTripsEscapedStrings() {
        String original = "quote\" backslash\\ newline\n tab\t unicodeé control";
        String json = Json.write(Map.of("s", original), false);

        assertEquals(original, ((Map<?, ?>) Json.parse(json)).get("s"));
    }

    @Test
    void decodesUnicodeEscapes() {
        assertEquals("é✓", Json.parse("\"\\u00e9\\u2713\""));
    }

    @Test
    void writesIntegralDoublesWithoutFraction() {
        assertEquals("[1, 2.5, null]", Json.write(Arrays.asList(1.0, 2.5, Double.NaN), true));
    }

    @Test
    void prettyModeKeepsScalarArraysInline() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("t", new double[] {0, 0.02, 0.04});
        tree.put("items", List.of(Map.of("k", 1)));

        String json = Json.write(tree, true);

        assertEquals("{\n  \"t\": [0, 0.02, 0.04],\n  \"items\": [\n    {\n      \"k\": 1\n    }\n  ]\n}", json);
    }

    @Test
    void reportsLineAndColumnOfSyntaxErrors() {
        JsonException error = assertThrows(JsonException.class, () -> Json.parse("{\n  \"a\": tru\n}"));

        assertTrue(error.getMessage().contains("line 2, column 8"), error.getMessage());
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(JsonException.class, () -> Json.parse("{} {}"));
    }

    @Test
    void rejectsTrailingCommas() {
        assertThrows(JsonException.class, () -> Json.parse("[1, 2,]"));
    }

    @Test
    void typedAccessorsReportFieldPath() {
        JsonObject root = Json.parseObject("{\"metadata\": {\"startedAt\": \"soon\"}}");

        JsonException error = assertThrows(JsonException.class,
                () -> root.getObject("metadata").getLong("startedAt"));

        assertEquals("$.metadata.startedAt must be a number", error.getMessage());
    }

    @Test
    void missingFieldReportsPath() {
        JsonObject root = Json.parseObject("{}");

        JsonException error = assertThrows(JsonException.class, () -> root.getString("id"));

        assertEquals("$.id is required but missing", error.getMessage());
    }

    @Test
    void readsNullArrayElementsAsNaN() {
        JsonObject root = Json.parseObject("{\"v\": [1, null, 3]}");

        double[] values = root.getDoubleArray("v");

        assertArrayEquals(new double[] {1, Double.NaN, 3}, values);
    }

    @Test
    void writerRejectsValueWithoutName() {
        JsonWriter writer = new JsonWriter(new StringBuilder(), false).beginObject();

        assertThrows(IllegalStateException.class, () -> writer.value(1));
    }

    @Test
    void preservesDoublePrecision() {
        double value = 0.1 + 0.2;
        String json = Json.write(List.of(value), false);

        assertEquals(value, (Double) ((List<?>) Json.parse(json)).get(0));
    }
}
