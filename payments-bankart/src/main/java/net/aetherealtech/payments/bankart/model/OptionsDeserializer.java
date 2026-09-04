package net.aetherealtech.payments.bankart.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads {@code options} in either of the two shapes the documentation publishes for it.
 *
 * <p>The declared {@code OptionsResponse} schema says an array of {@code {key, value}} objects. The
 * same endpoint's own {@code 200} example says a map:
 * <pre>{"options": {"bank1": "Bank One", "bank2": "Bank Two"}}</pre>
 * Which one arrives depends on the adapter behind {@code optionsName}, and nothing the caller does
 * decides it, so both are read into the same {@link Option} list and the map's insertion order is
 * kept — an options list is shown to a customer, and the gateway put it in the order it did for a
 * reason.
 */
public final class OptionsDeserializer extends JsonDeserializer<List<Option>> {

    @Override
    public List<Option> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<Option> options = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode entry : node) {
                options.add(new Option(text(entry.get("key")), text(entry.get("value"))));
            }
            return List.copyOf(options);
        }
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> field = fields.next();
                options.add(new Option(field.getKey(), text(field.getValue())));
            }
            return List.copyOf(options);
        }
        return List.of();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
