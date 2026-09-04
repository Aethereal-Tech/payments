package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * The parser, hard.
 *
 * <p>It is the piece of this module with no upstream to blame: every other class can be checked against
 * the SDK's source, and this one can only be checked against RFC 8259 and against what a hostile body
 * would do to it. A webhook endpoint is reachable by anyone.
 */
class JsonReaderTest {

    @Nested
    @DisplayName("shapes")
    class Shapes {

        @Test
        void readsNestedObjectsAndArrays() {
            final Map<String, Object> parsed = Json.parseObject("""
                    {"a":{"b":[1,{"c":"d"},[true,null]]},"e":{}}""");

            assertThat(Json.object(parsed, "e")).isEmpty();
            final List<Object> inner = Json.array(Json.object(parsed, "a"), "b");
            assertThat(inner).hasSize(3);
            assertThat(inner.get(0)).isEqualTo(new BigDecimal("1"));
            assertThat(inner.get(1)).isEqualTo(Map.of("c", "d"));
            assertThat(inner.get(2)).isEqualTo(Arrays.asList(Boolean.TRUE, null));
        }

        @Test
        void readsAnEmptyArrayAndAnEmptyObject() {
            assertThat(Json.parse("[]")).isEqualTo(List.of());
            assertThat(Json.parse("{}")).isEqualTo(Map.of());
        }

        @Test
        void readsTheThreeLiterals() {
            assertThat(Json.parse(" true ")).isEqualTo(Boolean.TRUE);
            assertThat(Json.parse("false")).isEqualTo(Boolean.FALSE);
            assertThat(Json.parse("null")).isNull();
        }

        @Test
        void skipsOnlyTheFourWhitespaceCharactersRfc8259Allows() {
            assertThat(Json.parseObject(" \t\r\n{ \"a\" : 1 } \t\r\n")).containsEntry("a", new BigDecimal("1"));
            assertThatThrownBy(() -> Json.parse("\u000B1"))
                    .isInstanceOf(Json.SyntaxException.class)
                    .hasMessageContaining("expected a number");
        }

        @Test
        void refusesATopLevelValueThatIsNotAnObject() {
            assertThatThrownBy(() -> Json.parseObject("[1,2]"))
                    .isInstanceOf(Json.SyntaxException.class)
                    .hasMessageContaining("expected a JSON object at the top level");
        }

        @Test
        void refusesNull() {
            assertThatThrownBy(() -> Json.parse(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("strings")
    class Strings {

        @Test
        void readsEveryTwoCharacterEscape() {
            assertThat(Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""))
                    .isEqualTo("\"\\/\b\f\n\r\t");
        }

        @Test
        void readsAUnicodeEscape() {
            assertThat(Json.parse("\"\\u0416\\u0430\""))
                    .isEqualTo("\u0416\u0430");
        }

        @Test
        void reassemblesASurrogatePairWrittenAsTwoEscapes() {
            final String parsed = (String) Json.parse("\"\\uD83D\\uDE00\"");

            assertThat(parsed).hasSize(2);
            assertThat(parsed.codePointCount(0, parsed.length())).isEqualTo(1);
            assertThat(parsed.codePointAt(0)).isEqualTo(0x1F600);
        }

        @Test
        void keepsANonAsciiCharacterThatWasNotEscaped() {
            assertThat(Json.parseObject("{\"name\":\"Благој\"}")).containsEntry("name", "Благој");
        }

        @Test
        void refusesAnUnescapedControlCharacter() {
            final Json.SyntaxException e = catchThrowableOfType(
                    Json.SyntaxException.class, () -> Json.parse("\"a\u0001b\""));

            assertThat(e).hasMessageContaining("unescaped control character U+0001");
            assertThat(e.offset()).isEqualTo(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"\\x\"", "\"\\u12\"", "\"\\u12G4\"", "\"unterminated", "\"trailing\\"})
        void refusesAMalformedString(final String text) {
            assertThat(catchThrowableOfType(Json.SyntaxException.class, () -> Json.parse(text)))
                    .isNotNull()
                    .hasMessageContaining("at offset");
        }
    }

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @Test
        void readsAnExponent() {
            assertThat(Json.parse("1.5e3")).isEqualTo(new BigDecimal("1.5e3"));
            assertThat(((BigDecimal) Json.parse("1.5E+3")).compareTo(new BigDecimal("1500"))).isZero();
            assertThat(((BigDecimal) Json.parse("15e-1")).compareTo(new BigDecimal("1.5"))).isZero();
        }

        @Test
        void readsNegativeZeroWithoutLosingIt() {
            assertThat(((BigDecimal) Json.parse("-0")).compareTo(BigDecimal.ZERO)).isZero();
            assertThat(((BigDecimal) Json.parse("-0.0")).compareTo(BigDecimal.ZERO)).isZero();
        }

        @Test
        void keepsTheScaleAMoneyAmountWasWrittenWith() {
            assertThat(Json.parse("29.90")).hasToString("29.90");
        }

        @ParameterizedTest
        @ValueSource(strings = {"01", "+1", ".5", "1.", "1.e3", "1e", "1e+", "-", "Infinity", "NaN", "0x1"})
        void refusesWhatIsNotAJsonNumber(final String text) {
            assertThat(catchThrowableOfType(Json.SyntaxException.class, () -> Json.parse(text)))
                    .as(text)
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        void refusesTrailingContentAfterTheTopLevelValue() {
            final Json.SyntaxException e = catchThrowableOfType(
                    Json.SyntaxException.class, () -> Json.parse("{\"a\":1} {\"b\":2}"));

            assertThat(e).hasMessageContaining("unexpected trailing content");
            assertThat(e.offset()).isEqualTo(8);
        }

        @Test
        void refusesNestingDeeperThanTheCap() {
            final String tooDeep = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);

            assertThat(catchThrowableOfType(Json.SyntaxException.class, () -> Json.parse(tooDeep)))
                    .hasMessageContaining("nesting deeper than " + Json.MAX_DEPTH);
        }

        @Test
        void acceptsNestingUpToTheCap() {
            final String atTheCap = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);

            assertThat(Json.parse(atTheCap)).isInstanceOf(List.class);
        }

        @Test
        void doesNotOverflowTheStackOnAHostileBody() {
            final String hostile = "[".repeat(200_000);

            assertThat(catchThrowableOfType(Json.SyntaxException.class, () -> Json.parse(hostile)))
                    .hasMessageContaining("nesting deeper than");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{", "{\"a\"", "{\"a\":}", "{\"a\":1", "{\"a\":1,}", "{a:1}", "{\"a\" 1}",
                "[", "[1", "[1,]", "[1 2]", "", "   ", "tru", "nul", "fals"})
        void refusesMalformedDocuments(final String text) {
            assertThat(catchThrowableOfType(Json.SyntaxException.class, () -> Json.parse(text)))
                    .as(text)
                    .isNotNull()
                    .hasMessageContaining("at offset");
        }

        @Test
        void lastValueWinsForADuplicateKey() {
            assertThat(Json.parseObject("{\"a\":1,\"a\":2}")).containsEntry("a", new BigDecimal("2"));
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        private final Map<String, Object> body = Json.parseObject("""
                {"text":"hi","number":19.99,"minor":1999,"big":9223372036854775808,"fraction":1.5,
                 "flag":true,"flagText":"TRUE","when":"2026-09-01T00:00:00Z","offset":"2026-09-01T02:00:00+02:00",
                 "date":"2026-09-01","broken":"not-a-date","nested":{"a":1},"list":[1,2],
                 "meta":{"s":"v","n":7,"b":false,"o":{"deep":1},"nul":null},"nothing":null,
                 "numericText":" 42 "}""");

        @Test
        void readsScalars() {
            assertThat(Json.string(body, "text")).isEqualTo("hi");
            assertThat(Json.decimal(body, "number")).isEqualTo(new BigDecimal("19.99"));
            assertThat(Json.integer(body, "minor")).isEqualTo(1999L);
            assertThat(Json.bool(body, "flag")).isTrue();
            assertThat(Json.bool(body, "flagText")).isTrue();
        }

        @Test
        void rendersANumberOrBooleanAsTextWhereAStringWasDocumented() {
            assertThat(Json.string(body, "number")).isEqualTo("19.99");
            assertThat(Json.string(body, "flag")).isEqualTo("true");
        }

        @Test
        void readsANumberWrittenAsAString() {
            assertThat(Json.decimal(body, "numericText")).isEqualTo(new BigDecimal("42"));
        }

        @Test
        void answersNullRatherThanThrowingForEverythingAbsentOrUnusable() {
            assertThat(Json.string(body, "missing")).isNull();
            assertThat(Json.string(null, "text")).isNull();
            assertThat(Json.string(body, "nested")).isNull();
            assertThat(Json.decimal(body, "text")).isNull();
            assertThat(Json.decimal(body, "nothing")).isNull();
            assertThat(Json.integer(body, "missing")).isNull();
            assertThat(Json.integer(body, "fraction")).isNull();
            assertThat(Json.integer(body, "big")).isNull();
            assertThat(Json.bool(body, "missing")).isFalse();
            assertThat(Json.bool(body, "number")).isFalse();
            assertThat(Json.instant(body, "missing")).isNull();
            assertThat(Json.instant(body, "broken")).isNull();
            assertThat(Json.object(body, "text")).isNull();
            assertThat(Json.array(body, "missing")).isEmpty();
            assertThat(Json.objects(body, "list")).isEmpty();
            assertThat(Json.stringMap(body, "missing")).isEmpty();
        }

        @Test
        void readsEveryTimestampShapeTheGatewayUses() {
            assertThat(Json.instant(body, "when")).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
            assertThat(Json.instant(body, "offset")).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
            assertThat(Json.instant(body, "date")).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        }

        @Test
        void flattensMetadataAndKeepsANestedValueAsItsJson() {
            assertThat(Json.stringMap(body, "meta"))
                    .containsEntry("s", "v")
                    .containsEntry("n", "7")
                    .containsEntry("b", "false")
                    .containsEntry("o", "{\"deep\":1}")
                    .doesNotContainKey("nul");
        }

        @Test
        void readsAnArrayOfObjects() {
            final Map<String, Object> withRows = Json.parseObject("{\"items\":[{\"id\":\"a\"},7,{\"id\":\"b\"}]}");

            assertThat(Json.objects(withRows, "items")).hasSize(2);
            assertThat(Json.string(Json.objects(withRows, "items").get(1), "id")).isEqualTo("b");
        }
    }

    @Nested
    @DisplayName("camelCase aliases")
    class CamelAliases {

        @Test
        void addsACamelCaseTwinForEverySnakeCaseKeyWithoutLosingTheOriginal() {
            final Map<String, Object> aliased =
                    Json.withCamelAliases(Json.parseObject("{\"current_period_end\":\"x\",\"id\":\"y\"}"));

            assertThat(aliased).containsEntry("currentPeriodEnd", "x").containsEntry("current_period_end", "x");
            assertThat(aliased).containsEntry("id", "y");
        }

        @Test
        void leavesAnAlreadyCamelCaseKeyAlone() {
            assertThat(Json.withCamelAliases(Map.of("currentPeriodEnd", "x")))
                    .containsExactlyEntriesOf(Map.of("currentPeriodEnd", "x"));
        }

        @Test
        void doesNotOverwriteAKeyThatIsAlreadyThere() {
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("amountMinor", "camel");
            source.put("amount_minor", "snake");

            assertThat(Json.withCamelAliases(source)).containsEntry("amountMinor", "camel");
        }

        @Test
        void doesNotDescendIntoNestedObjects() {
            final Map<String, Object> aliased =
                    Json.withCamelAliases(Json.parseObject("{\"metadata\":{\"order_ref\":\"a\"}}"));

            assertThat(Json.object(aliased, "metadata")).containsOnlyKeys("order_ref");
        }

        @Test
        void passesNullThrough() {
            assertThat(Json.withCamelAliases(null)).isNull();
        }

        @Test
        void convertsOnlyWhereThereIsAnUnderscore() {
            assertThat(Json.snakeToCamel("plain")).isEqualTo("plain");
            assertThat(Json.snakeToCamel("x402_url")).isEqualTo("x402Url");
            assertThat(Json.snakeToCamel("a_b_c")).isEqualTo("aBC");
            assertThat(Json.snakeToCamel("trailing_")).isEqualTo("trailing");
        }
    }
}
