package dev.brex.core.json;

import java.util.List;
import java.util.Map;

/**
 * Entry point for B-rex's dependency-free JSON support.
 *
 * <p>B-rex deliberately avoids JSON libraries so that the robot-side modules add nothing to an
 * FTC TeamCode dependency graph.
 */
public final class Json {

    private Json() {
    }

    /** Parses a JSON document into a Map/List/String/Double/Boolean/null tree. */
    public static Object parse(String text) {
        return new JsonParser(text).parseDocument();
    }

    /** Parses a JSON document whose root must be an object. */
    public static JsonObject parseObject(String text) {
        Object root = parse(text);
        if (!(root instanceof Map)) {
            throw new JsonException("Expected a JSON object at the document root");
        }
        return JsonObject.wrap(root, "$");
    }

    /** Serializes a Map/List/String/Number/Boolean/null tree. */
    public static String write(Object tree, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        new JsonWriter(sb, pretty).tree(tree);
        return sb.toString();
    }

    static void writeTree(JsonWriter writer, Object value) {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof String) {
            writer.value((String) value);
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte) {
            writer.value(((Number) value).longValue());
        } else if (value instanceof Number) {
            writer.value(((Number) value).doubleValue());
        } else if (value instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                writer.name(String.valueOf(entry.getKey()));
                writeTree(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof List) {
            writer.beginArray();
            for (Object element : (List<?>) value) {
                writeTree(writer, element);
            }
            writer.endArray();
        } else if (value instanceof double[]) {
            writer.beginArray();
            for (double d : (double[]) value) {
                writer.value(d);
            }
            writer.endArray();
        } else if (value instanceof Enum) {
            writer.value(((Enum<?>) value).name());
        } else {
            throw new JsonException("Cannot serialize " + value.getClass().getName() + " to JSON");
        }
    }
}
