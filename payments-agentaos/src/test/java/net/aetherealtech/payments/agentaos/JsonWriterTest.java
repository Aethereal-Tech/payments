package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.agentaos.internal.Json;

/** The writer: exact bytes, because that is what the gateway parses. */
class JsonWriterTest {

    @Test
    void writesFieldsInIterationOrder() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("linkId", "pl_9d3");
        fields.put("amount", new BigDecimal("29.99"));
        fields.put("currency", "EUR");

        assertThat(Json.write(fields))
                .isEqualTo("{\"linkId\":\"pl_9d3\",\"amount\":29.99,\"currency\":\"EUR\"}");
    }

    @Test
    void omitsANullValueRatherThanWritingIt() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("a", null);
        fields.put("b", 1);
        fields.put("c", null);

        assertThat(Json.write(fields)).isEqualTo("{\"b\":1}");
    }

    @Test
    void writesAnEmptyObjectForNoFields() {
        assertThat(Json.write(Map.of())).isEqualTo("{}");
    }

    @Test
    void writesNestedObjectsAndArrays() {
        final Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("key", "vat");
        inner.put("required", true);
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("checkoutFields", List.of(inner));
        fields.put("networks", List.of("eip155:8453", "eip155:1"));

        assertThat(Json.write(fields)).isEqualTo(
                "{\"checkoutFields\":[{\"key\":\"vat\",\"required\":true}],"
                        + "\"networks\":[\"eip155:8453\",\"eip155:1\"]}");
    }

    @Test
    void writesADecimalWithoutAnExponent() {
        // new BigDecimal("1E+3") is a legitimate way to hold 1000, and its toString carries the exponent
        // through to whatever wire is listening.
        assertThat(Json.writeValue(new BigDecimal("1E+3"))).isEqualTo("1000");
    }

    @Test
    void escapesWhatRfc8259RequiresAndNothingElse() {
        final String control = String.valueOf((char) 0x01);

        assertThat(Json.string("a\"b\\c\nd\te" + control + "f"))
                .isEqualTo("\"a\\\"b\\\\c\\nd\\te\\u0001f\"");
        assertThat(Json.string("\b\f\r")).isEqualTo("\"\\b\\f\\r\"");
    }

    @Test
    void passesNonAsciiThroughLiterally() {
        assertThat(Json.string("Благој")).isEqualTo("\"Благој\"");
    }

    @Test
    void writesALiteralNullAsAValueRatherThanOmittingIt() {
        assertThat(Json.writeValue(null)).isEqualTo("null");
        assertThat(Json.writeValue(List.of())).isEqualTo("[]");
        final List<Object> withHole = new ArrayList<>();
        withHole.add("a");
        withHole.add(null);

        assertThat(Json.writeValue(withHole)).isEqualTo("[\"a\",null]");
    }

    @Test
    void refusesATypeItHasNoRenderingFor() {
        assertThatThrownBy(() -> Json.writeValue(new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no JSON rendering");
    }

    @Test
    void roundTripsThroughItsOwnParser() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("text", "line\nbreak \"quoted\"");
        fields.put("emoji", "😀");
        fields.put("amount", new BigDecimal("0.10"));

        final Map<String, Object> parsed = Json.parseObject(Json.write(fields));

        assertThat(Json.string(parsed, "text")).isEqualTo("line\nbreak \"quoted\"");
        assertThat(Json.string(parsed, "emoji")).isEqualTo("😀");
        assertThat(Json.decimal(parsed, "amount")).isEqualTo(new BigDecimal("0.10"));
    }
}
