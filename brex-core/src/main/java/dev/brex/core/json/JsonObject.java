package dev.brex.core.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A typed, read-only view over a parsed JSON object.
 *
 * <p>Accessors report the full path of a missing or mistyped field (for example
 * {@code $.metadata.startedAt}) so that a corrupt run file produces an actionable error.
 */
public final class JsonObject {

    private final Map<String, Object> values;
    private final String path;

    private JsonObject(Map<String, Object> values, String path) {
        this.values = values;
        this.path = path;
    }

    @SuppressWarnings("unchecked")
    static JsonObject wrap(Object value, String path) {
        if (!(value instanceof Map)) {
            throw new JsonException(path + " must be an object");
        }
        return new JsonObject((Map<String, Object>) value, path);
    }

    public String path() {
        return path;
    }

    public boolean has(String key) {
        return values.containsKey(key) && values.get(key) != null;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public Object raw(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object value = require(key);
        if (!(value instanceof String)) {
            throw typeError(key, "a string");
        }
        return (String) value;
    }

    public String optString(String key, String fallback) {
        return has(key) ? getString(key) : fallback;
    }

    public double getDouble(String key) {
        Object value = require(key);
        if (!(value instanceof Number)) {
            throw typeError(key, "a number");
        }
        return ((Number) value).doubleValue();
    }

    /** Returns a double; JSON {@code null} (used for non-finite values) reads as NaN. */
    public double optDouble(String key, double fallback) {
        if (!values.containsKey(key)) {
            return fallback;
        }
        Object value = values.get(key);
        if (value == null) {
            return Double.NaN;
        }
        return getDouble(key);
    }

    public long getLong(String key) {
        double value = getDouble(key);
        if (value != Math.rint(value)) {
            throw typeError(key, "an integer");
        }
        return (long) value;
    }

    public long optLong(String key, long fallback) {
        return has(key) ? getLong(key) : fallback;
    }

    public int getInt(String key) {
        long value = getLong(key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw typeError(key, "a 32-bit integer");
        }
        return (int) value;
    }

    public boolean getBoolean(String key) {
        Object value = require(key);
        if (!(value instanceof Boolean)) {
            throw typeError(key, "a boolean");
        }
        return (Boolean) value;
    }

    public boolean optBoolean(String key, boolean fallback) {
        return has(key) ? getBoolean(key) : fallback;
    }

    public JsonObject getObject(String key) {
        return wrap(require(key), path + "." + key);
    }

    /** Returns the nested object, or an empty object when the key is absent. */
    public JsonObject optObject(String key) {
        return has(key) ? getObject(key) : new JsonObject(new LinkedHashMap<String, Object>(), path + "." + key);
    }

    public List<JsonObject> getObjectList(String key) {
        List<?> raw = getList(key);
        List<JsonObject> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            result.add(wrap(raw.get(i), path + "." + key + "[" + i + "]"));
        }
        return result;
    }

    public List<JsonObject> optObjectList(String key) {
        return has(key) ? getObjectList(key) : Collections.<JsonObject>emptyList();
    }

    public List<?> getList(String key) {
        Object value = require(key);
        if (!(value instanceof List)) {
            throw typeError(key, "an array");
        }
        return (List<?>) value;
    }

    /** Reads an array of numbers; {@code null} elements read as NaN. */
    public double[] getDoubleArray(String key) {
        List<?> raw = getList(key);
        double[] result = new double[raw.size()];
        for (int i = 0; i < result.length; i++) {
            Object element = raw.get(i);
            if (element == null) {
                result[i] = Double.NaN;
            } else if (element instanceof Number) {
                result[i] = ((Number) element).doubleValue();
            } else {
                throw new JsonException(path + "." + key + "[" + i + "] must be a number");
            }
        }
        return result;
    }

    public List<String> getStringList(String key) {
        List<?> raw = getList(key);
        List<String> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (element != null && !(element instanceof String)) {
                throw new JsonException(path + "." + key + "[" + i + "] must be a string");
            }
            result.add((String) element);
        }
        return result;
    }

    /** Reads an object whose values are all strings. */
    public Map<String, String> getStringMap(String key) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!has(key)) {
            return result;
        }
        JsonObject object = getObject(key);
        for (String k : object.keys()) {
            Object value = object.raw(k);
            result.put(k, value == null ? null : String.valueOf(value));
        }
        return result;
    }

    private Object require(String key) {
        if (!values.containsKey(key) || values.get(key) == null) {
            throw new JsonException(path + "." + key + " is required but missing");
        }
        return values.get(key);
    }

    private JsonException typeError(String key, String expected) {
        return new JsonException(path + "." + key + " must be " + expected);
    }
}
