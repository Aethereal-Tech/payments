package net.aetherealtech.bankart.model;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads {@code returnData} in either of the two shapes the documentation publishes for it.
 *
 * <p>The notification examples send the card fields flat, tagged with {@code "_TYPE": "cardData"}:
 * <pre>{"returnData": {"_TYPE": "cardData", "lastFourDigits": "1111", ...}}</pre>
 * while the status API's example nests them under a discriminator key instead:
 * <pre>{"returnData": {"creditcardData": {"lastFourDigits": "4321", ...}}}</pre>
 *
 * <p>Which one a given deployment sends is not something a client can decide, so it accepts both. A
 * {@code returnData} of some other documented variant — iban, phone or wallet — reads as null
 * rather than as a card stripped of its meaning.
 */
public final class CardDataDeserializer extends JsonDeserializer<CardData> {

    private static final String[] WRAPPER_KEYS = {"cardData", "creditcardData"};

    @Override
    public CardData deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        if (node == null || !node.isObject()) {
            return null;
        }

        for (String key : WRAPPER_KEYS) {
            JsonNode wrapped = node.get(key);
            if (wrapped != null && wrapped.isObject()) {
                return withType(mapper, (ObjectNode) wrapped, key);
            }
        }

        if (node.has("_TYPE") || node.has("lastFourDigits") || node.has("cardHolder")) {
            return mapper.treeToValue(node, CardData.class);
        }
        return null;
    }

    /** The nested shape carries no {@code _TYPE}, so the wrapper key supplies the discriminator. */
    private static CardData withType(ObjectMapper mapper, ObjectNode wrapped, String key) throws IOException {
        ObjectNode copy = wrapped.deepCopy();
        if (!copy.has("_TYPE")) {
            copy.put("_TYPE", key);
        }
        return mapper.treeToValue(copy, CardData.class);
    }
}
