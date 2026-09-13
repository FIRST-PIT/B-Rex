package dev.brex.core.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A streaming JSON writer.
 *
 * <p>In pretty mode, objects are indented one member per line while arrays of scalars stay on a
 * single line. Recorded runs are dominated by long numeric arrays, so this keeps files both
 * readable and compact.
 *
 * <p>Non-finite doubles (NaN, infinity) have no JSON representation and are written as
 * {@code null}.
 */
public final class JsonWriter {

    private static final class Scope {
        final boolean object;
        boolean empty = true;
        boolean containsContainer;
        boolean expectingValue;

        Scope(boolean object) {
            this.object = object;
        }
    }

    private final Appendable out;
    private final boolean pretty;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private boolean rootWritten;

    public JsonWriter(Appendable out, boolean pretty) {
        this.out = out;
        this.pretty = pretty;
    }

    public JsonWriter beginObject() {
        beforeValue(true);
        append('{');
        scopes.push(new Scope(true));
        return this;
    }

    public JsonWriter endObject() {
        Scope scope = pop(true);
        if (!scope.empty && pretty) {
            newline(scopes.size());
        }
        append('}');
        return this;
    }

    public JsonWriter beginArray() {
        beforeValue(true);
        append('[');
        scopes.push(new Scope(false));
        return this;
    }

    public JsonWriter endArray() {
        Scope scope = pop(false);
        if (scope.containsContainer && pretty) {
            newline(scopes.size());
        }
        append(']');
        return this;
    }

    public JsonWriter name(String name) {
        Scope scope = scopes.peek();
        if (scope == null || !scope.object || scope.expectingValue) {
            throw new IllegalStateException("name() is only valid directly inside an object");
        }
        if (!scope.empty) {
            append(',');
        }
        if (pretty) {
            newline(scopes.size());
        }
        writeString(name);
        appendRaw(pretty ? ": " : ":");
        scope.empty = false;
        scope.expectingValue = true;
        return this;
    }

    public JsonWriter value(String value) {
        if (value == null) {
            return nullValue();
        }
        beforeValue(false);
        writeString(value);
        return this;
    }

    public JsonWriter value(double value) {
        beforeValue(false);
        appendRaw(formatNumber(value));
        return this;
    }

    public JsonWriter value(long value) {
        beforeValue(false);
        appendRaw(Long.toString(value));
        return this;
    }

    public JsonWriter value(boolean value) {
        beforeValue(false);
        appendRaw(value ? "true" : "false");
        return this;
    }

    public JsonWriter nullValue() {
        beforeValue(false);
        appendRaw("null");
        return this;
    }

    /** Writes a Map/List/String/Number/Boolean/null tree. */
    public JsonWriter tree(Object value) {
        Json.writeTree(this, value);
        return this;
    }

    /** Formats a double the way this writer would, exposed for consistent human-readable output. */
    public static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "null";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private void beforeValue(boolean container) {
        Scope scope = scopes.peek();
        if (scope == null) {
            if (rootWritten) {
                throw new IllegalStateException("JSON document already has a root value");
            }
            rootWritten = true;
            return;
        }
        if (scope.object) {
            if (!scope.expectingValue) {
                throw new IllegalStateException("Object values must be preceded by name()");
            }
            scope.expectingValue = false;
            return;
        }
        if (!scope.empty) {
            append(',');
            if (pretty && !container) {
                append(' ');
            }
        }
        if (container) {
            scope.containsContainer = true;
            if (pretty) {
                newline(scopes.size());
            }
        }
        scope.empty = false;
    }

    private Scope pop(boolean object) {
        Scope scope = scopes.poll();
        if (scope == null || scope.object != object) {
            throw new IllegalStateException("Mismatched " + (object ? "endObject()" : "endArray()"));
        }
        if (scope.expectingValue) {
            throw new IllegalStateException("Object member is missing its value");
        }
        return scope;
    }

    private void writeString(String s) {
        append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    appendRaw("\\\"");
                    break;
                case '\\':
                    appendRaw("\\\\");
                    break;
                case '\n':
                    appendRaw("\\n");
                    break;
                case '\r':
                    appendRaw("\\r");
                    break;
                case '\t':
                    appendRaw("\\t");
                    break;
                case '\b':
                    appendRaw("\\b");
                    break;
                case '\f':
                    appendRaw("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        appendRaw(String.format("\\u%04x", (int) c));
                    } else {
                        append(c);
                    }
            }
        }
        append('"');
    }

    private void newline(int depth) {
        append('\n');
        for (int i = 0; i < depth; i++) {
            appendRaw("  ");
        }
    }

    private void append(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void appendRaw(String s) {
        try {
            out.append(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
