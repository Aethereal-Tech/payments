package net.aetherealtech.payments.agentaos.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * NOT API. The JSON reader and writer this adapter's request bodies and gateway responses go through.
 *
 * <p>It exists because this module ships with ZERO runtime dependencies, and that is not tidiness: a
 * known consumer of this adapter runs Jackson 3. A transitive Jackson 2 is not a version conflict Maven
 * can mediate — {@code com.fasterxml.jackson.core:jackson-databind} and
 * {@code tools.jackson.core:jackson-databind} are different coordinates carrying different package
 * names, so both land on the classpath and which one wins is decided by whichever code imported which.
 * The documents parsed here are AgentaOS's own — small, flat, and fully described by this adapter's
 * model classes — so the reach of a general JSON library is not what is needed; not costing a consumer
 * a second Jackson is.
 *
 * <p>The reader is strict on purpose. A webhook endpoint is public and unauthenticated until the
 * signature is checked, so this refuses input rather than repairing it, caps nesting depth so a hostile
 * body cannot exhaust the stack through the recursive descent, and names the offset in every message —
 * a parser that silently accepts malformed input is worse than no parser at all.
 *
 * <p>The ACCESSORS are the opposite: forgiving, because nearly every field on this gateway is optional
 * and an absent key is the normal case rather than an error. They answer null (or an empty collection)
 * for anything missing or of the wrong type, and never throw.
 */
public final class Json {

    /**
     * How deep a document may nest before it is refused.
     *
     * <p>Sixty-four is far beyond anything this gateway sends — its deepest document nests four — and
     * far below what recursive descent needs to overflow a default stack.
     */
    public static final int MAX_DEPTH = 64;

    private Json() {
    }

    // ---------------------------------------------------------------- writing

    /**
     * One JSON object from {@code fields}, in iteration order.
     *
     * <p>A null VALUE is omitted rather than written as {@code null}, because every request body this
     * adapter sends is a sparse set of optional parameters and AgentaOS distinguishes an absent
     * parameter from an explicitly null one. Pass a {@link java.util.LinkedHashMap} to control the order
     * — the tests assert the exact bytes.
     */
    public static String write(final Map<String, Object> fields) {
        final StringBuilder out = new StringBuilder();
        appendObject(out, fields);
        return out.toString();
    }

    /**
     * One JSON value: a string, a number, a boolean, {@code null}, a map, or a collection.
     *
     * <p>Unlike {@link #write(Map)} this renders a null as the literal {@code null} — it is a value in
     * its own right here rather than an omitted parameter.
     *
     * @throws IllegalArgumentException for a type with no JSON rendering, which is a bug in the caller
     */
    public static String writeValue(final Object value) {
        final StringBuilder out = new StringBuilder();
        appendValue(out, value);
        return out.toString();
    }

    /** The quoted, escaped literal for {@code value} — {@code "like this"}, with the quotes. */
    public static String string(final String value) {
        final StringBuilder out = new StringBuilder();
        appendString(out, value);
        return out.toString();
    }

    // ---------------------------------------------------------------- reading

    /**
     * One JSON document as {@link Map}, {@link List}, {@link String}, {@link BigDecimal},
     * {@link Boolean} or null.
     *
     * <p>Anything after the top-level value is refused: a body that parses as a number followed by
     * rubbish is not a body this adapter should act on half of.
     *
     * @throws SyntaxException naming the offset, for anything malformed
     */
    public static Object parse(final String text) {
        Objects.requireNonNull(text, "text must not be null");
        final Parser parser = new Parser(text);
        final Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing content after the top-level value");
        }
        return value;
    }

    /**
     * The same, insisting the top-level value is an object — which every AgentaOS document is.
     *
     * @throws SyntaxException if the document is malformed or is not an object
     */
    public static Map<String, Object> parseObject(final String text) {
        final Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new SyntaxException("expected a JSON object at the top level at offset 0", 0);
        }
        return asObject(value);
    }

    // ---------------------------------------------------------------- accessors

    /**
     * One string field. Null when absent.
     *
     * <p>A number or boolean is rendered as text rather than refused: a gateway that sends {@code 1999}
     * where it documents {@code "1999"} is drift this adapter should survive, not a reason to drop a
     * verified webhook.
     */
    public static String string(final Map<String, Object> object, final String key) {
        final Object value = get(object, key);
        return switch (value) {
            case null -> null;
            case String s -> s;
            case BigDecimal d -> d.toPlainString();
            case Boolean b -> b.toString();
            default -> null;
        };
    }

    /** One decimal field, accepting a JSON number or a numeric string. Null when absent or unreadable. */
    public static BigDecimal decimal(final Map<String, Object> object, final String key) {
        final Object value = get(object, key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** One integer field. Null when absent, unreadable, or not an exact integer. */
    public static Long integer(final Map<String, Object> object, final String key) {
        final BigDecimal decimal = decimal(object, key);
        if (decimal == null) {
            return null;
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** One boolean field, false when absent — which is what every flag on this gateway means absent. */
    public static boolean bool(final Map<String, Object> object, final String key) {
        final Object value = get(object, key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof String text && "true".equalsIgnoreCase(text);
    }

    /**
     * One ISO-8601 timestamp — an instant, an offset date-time, or a bare {@code YYYY-MM-DD} date, which
     * is what {@code dueDate} carries. Null when absent or unparseable.
     *
     * <p>Unparseable answers null rather than throwing because these are read after a webhook has
     * already authenticated: a date this adapter cannot read is a field to leave empty, not a reason to
     * refuse an event the provider will then redeliver for days.
     */
    public static Instant instant(final Map<String, Object> object, final String key) {
        final String text = string(object, key);
        if (text == null || text.isBlank()) {
            return null;
        }
        final String trimmed = text.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // Falls through to the two narrower shapes below.
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Falls through to the date-only shape below.
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** One nested object. Null when absent or not an object, so a caller can test for presence. */
    public static Map<String, Object> object(final Map<String, Object> object, final String key) {
        final Object value = get(object, key);
        return value instanceof Map ? asObject(value) : null;
    }

    /** One nested array. Empty when absent, since a caller iterating one never wants a null. */
    public static List<Object> array(final Map<String, Object> object, final String key) {
        final Object value = get(object, key);
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        return List.of();
    }

    /** An array of objects, skipping any element that is not one. Empty when absent. */
    public static List<Map<String, Object>> objects(final Map<String, Object> object, final String key) {
        final List<Map<String, Object>> out = new ArrayList<>();
        array(object, key).forEach(element -> {
            if (element instanceof Map) {
                out.add(asObject(element));
            }
        });
        return List.copyOf(out);
    }

    /**
     * A free-form metadata map, flattened to strings.
     *
     * <p>A nested object or array keeps its JSON text rather than being dropped: metadata is the
     * merchant's own, this adapter did not put it there, and losing part of it silently would be a bug
     * reported as "my metadata disappeared".
     */
    public static Map<String, String> stringMap(final Map<String, Object> object, final String key) {
        final Map<String, Object> source = object(object, key);
        if (source == null) {
            return Map.of();
        }
        final Map<String, String> out = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (value != null) {
                out.put(name, value instanceof String text ? text : writeValue(value));
            }
        });
        return Map.copyOf(out);
    }

    /**
     * A copy of {@code object} in which every {@code snake_case} key also appears in {@code camelCase}.
     *
     * <p>One level only, and the originals are kept. This exists for the webhook payload alone: the
     * SDK's own {@code snakeToCamel} runs in its HTTP layer, so a REST response is snake_case and read
     * as such, while a webhook body never passes through that layer and is typed in {@code types.ts}
     * with camelCase field names. Reading both spellings is how this adapter survives being wrong about
     * which, and the aliases stop at the top level so a merchant's own metadata keys are not rewritten.
     */
    public static Map<String, Object> withCamelAliases(final Map<String, Object> object) {
        if (object == null) {
            return null;
        }
        final Map<String, Object> out = new LinkedHashMap<>(object);
        object.forEach((key, value) -> {
            final String camel = snakeToCamel(key);
            if (!camel.equals(key)) {
                out.putIfAbsent(camel, value);
            }
        });
        return out;
    }

    /** {@code current_period_end} becomes {@code currentPeriodEnd}; anything else is returned as it came. */
    public static String snakeToCamel(final String key) {
        if (key.indexOf('_') < 0) {
            return key;
        }
        final StringBuilder out = new StringBuilder(key.length());
        boolean upper = false;
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            if (c == '_') {
                upper = true;
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- internals

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(final Object value) {
        return (Map<String, Object>) value;
    }

    private static Object get(final Map<String, Object> object, final String key) {
        return object == null ? null : object.get(key);
    }

    private static void appendObject(final StringBuilder out, final Map<String, ?> fields) {
        out.append('{');
        boolean first = true;
        for (final Map.Entry<String, ?> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, entry.getKey());
            out.append(':');
            appendValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void appendValue(final StringBuilder out, final Object value) {
        switch (value) {
            case null -> out.append("null");
            case String text -> appendString(out, text);
            case BigDecimal decimal -> out.append(decimal.toPlainString());
            case Boolean flag -> out.append(flag.toString());
            case Number number -> out.append(number.toString());
            case Map<?, ?> map -> appendObject(out, castKeys(map));
            case Iterable<?> items -> appendArray(out, items);
            default -> throw new IllegalArgumentException(
                    "no JSON rendering for " + value.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castKeys(final Map<?, ?> map) {
        return (Map<String, ?>) map;
    }

    private static void appendArray(final StringBuilder out, final Iterable<?> items) {
        out.append('[');
        boolean first = true;
        for (final Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendValue(out, item);
        }
        out.append(']');
    }

    private static void appendString(final StringBuilder out, final String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Recursive descent over one document, depth-capped and refusing anything it cannot read. */
    private static final class Parser {

        private final String src;
        private int pos;
        private int depth;

        private Parser(final String src) {
            this.src = src;
        }

        private boolean atEnd() {
            return pos >= src.length();
        }

        private Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end of input where a value was expected");
            }
            return switch (src.charAt(pos)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            enter();
            pos++;
            final Map<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && src.charAt(pos) == '}') {
                pos++;
                depth--;
                return out;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || src.charAt(pos) != '"') {
                    throw error("expected a quoted object key");
                }
                final String key = readString();
                skipWhitespace();
                if (atEnd() || src.charAt(pos) != ':') {
                    throw error("expected ':' after an object key");
                }
                pos++;
                // A duplicate key overwrites: RFC 8259 leaves the behaviour undefined, and last-wins is
                // what every mainstream parser does, so it is what a gateway would have been tested against.
                out.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated object");
                }
                final char c = src.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    depth--;
                    return out;
                }
                throw error("expected ',' or '}' in an object");
            }
        }

        private List<Object> readArray() {
            enter();
            pos++;
            final List<Object> out = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && src.charAt(pos) == ']') {
                pos++;
                depth--;
                return out;
            }
            while (true) {
                out.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated array");
                }
                final char c = src.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    depth--;
                    return out;
                }
                throw error("expected ',' or ']' in an array");
            }
        }

        private String readString() {
            pos++;
            final StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                final char c = src.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    readEscape(out);
                } else if (c < 0x20) {
                    throw errorAt(pos - 1, "unescaped control character U+"
                            + String.format(Locale.ROOT, "%04X", (int) c) + " in a string");
                } else {
                    out.append(c);
                }
            }
        }

        private void readEscape(final StringBuilder out) {
            if (atEnd()) {
                throw error("unterminated escape sequence");
            }
            final int at = pos;
            final char c = src.charAt(pos++);
            switch (c) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                // Each escape appends one UTF-16 code unit, so a surrogate PAIR written as two
                // consecutive escapes reassembles itself in the buffer with no special handling.
                case 'u' -> out.append(readHexQuad());
                default -> throw errorAt(at, "invalid escape \\" + c);
            }
        }

        private char readHexQuad() {
            if (pos + 4 > src.length()) {
                throw error("truncated \\u escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                final int digit = Character.digit(src.charAt(pos + i), 16);
                if (digit < 0) {
                    throw errorAt(pos + i, "invalid hex digit in a \\u escape");
                }
                value = value * 16 + digit;
            }
            pos += 4;
            return (char) value;
        }

        private BigDecimal readNumber() {
            final int start = pos;
            if (!atEnd() && src.charAt(pos) == '-') {
                pos++;
            }
            if (atEnd() || !isDigit(src.charAt(pos))) {
                throw error("expected a number");
            }
            if (src.charAt(pos) == '0') {
                pos++;
                if (!atEnd() && isDigit(src.charAt(pos))) {
                    throw error("a leading zero is not a valid number");
                }
            } else {
                while (!atEnd() && isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && src.charAt(pos) == '.') {
                pos++;
                requireDigits("expected a digit after the decimal point");
            }
            if (!atEnd() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (!atEnd() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                requireDigits("expected a digit in the exponent");
            }
            return new BigDecimal(src.substring(start, pos));
        }

        private void requireDigits(final String message) {
            final int from = pos;
            while (!atEnd() && isDigit(src.charAt(pos))) {
                pos++;
            }
            if (pos == from) {
                throw error(message);
            }
        }

        private Object readLiteral(final String literal, final Object value) {
            if (!src.startsWith(literal, pos)) {
                throw error("expected " + literal);
            }
            pos += literal.length();
            return value;
        }

        private void enter() {
            depth++;
            if (depth > MAX_DEPTH) {
                throw error("nesting deeper than " + MAX_DEPTH + " levels");
            }
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                final char c = src.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        private static boolean isDigit(final char c) {
            return c >= '0' && c <= '9';
        }

        private SyntaxException error(final String message) {
            return errorAt(pos, message);
        }

        private SyntaxException errorAt(final int at, final String message) {
            return new SyntaxException(message + " at offset " + at, at);
        }
    }

    /**
     * Malformed JSON, with the offset it went wrong at.
     *
     * <p>The offset is the whole point. A gateway that changes a field's shape and an attacker probing a
     * webhook endpoint both arrive as "could not parse", and only the position tells an operator which
     * of the two they are looking at.
     */
    public static final class SyntaxException extends RuntimeException {

        private final int offset;

        public SyntaxException(final String message, final int offset) {
            super(message);
            this.offset = offset;
        }

        /** Where in the document parsing stopped, counted in {@code char}s from zero. */
        public int offset() {
            return offset;
        }
    }
}
