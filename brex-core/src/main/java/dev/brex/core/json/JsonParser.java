package dev.brex.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON parser.
 *
 * <p>Objects become {@link LinkedHashMap}s (preserving key order), arrays become {@link ArrayList}s,
 * numbers become {@link Double}s, and {@code null} becomes Java {@code null}.
 */
final class JsonParser {

    private final String text;
    private int pos;

    JsonParser(String text) {
        this.text = text;
    }

    Object parseDocument() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (pos != text.length()) {
            throw error("unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a quoted object key");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == '}') {
                return object;
            }
            if (next != ',') {
                pos--;
                throw error("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> parseArray() {
        List<Object> array = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue());
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == ']') {
                return array;
            }
            if (next != ',') {
                pos--;
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private String parseString() {
        pos++; // opening quote
        StringBuilder sb = null;
        int start = pos;
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos);
            if (c == '"') {
                String result = sb == null ? text.substring(start, pos) : sb.append(text, start, pos).toString();
                pos++;
                return result;
            }
            if (c == '\\') {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(text, start, pos);
                pos++;
                sb.append(parseEscape());
                start = pos;
                continue;
            }
            if (c < 0x20) {
                throw error("unescaped control character in string");
            }
            pos++;
        }
    }

    private char parseEscape() {
        if (pos >= text.length()) {
            throw error("unterminated escape sequence");
        }
        char c = text.charAt(pos++);
        switch (c) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                if (pos + 4 > text.length()) {
                    throw error("truncated unicode escape");
                }
                try {
                    char decoded = (char) Integer.parseInt(text.substring(pos, pos + 4), 16);
                    pos += 4;
                    return decoded;
                } catch (NumberFormatException e) {
                    throw error("invalid unicode escape");
                }
            default:
                pos--;
                throw error("invalid escape '\\" + c + "'");
        }
    }

    private Double parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        consumeDigits();
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            consumeDigits();
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            consumeDigits();
        }
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            pos = start;
            throw error("invalid number");
        }
    }

    private void consumeDigits() {
        int start = pos;
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
            pos++;
        }
        if (pos == start) {
            throw error("expected a digit");
        }
    }

    private void expectLiteral(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw error("invalid literal");
        }
        pos += literal.length();
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("expected '" + expected + "'");
        }
        pos++;
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return;
            }
            pos++;
        }
    }

    private JsonException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new JsonException("Invalid JSON at line " + line + ", column " + column + ": " + message);
    }
}
