package net.aetherealtech.payments.bankart.model;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads {@code returnData} in every shape the documentation publishes for it.
 *
 * <p>Jackson's own {@code @JsonTypeInfo} discriminator would handle the declared {@code oneOf} on
 * {@code _TYPE} and nothing else, and there are two documented departures from it:
 *
 * <ul>
 *   <li>The notification examples send the fields flat with the discriminator alongside them —
 *       <pre>{"returnData": {"_TYPE": "cardData", "lastFourDigits": "1111"}}</pre>
 *       while the status API's example nests them under a key instead, with no {@code _TYPE} at all:
 *       <pre>{"returnData": {"creditcardData": {"lastFourDigits": "4321"}}}</pre>
 *       {@code creditcardData} is that nested spelling only; it is not a fifth variant and it is not
 *       in the discriminator's mapping.</li>
 *   <li>A payload carrying neither a {@code _TYPE} nor a recognised wrapper is read as a card when
 *       it carries card fields, because that is what the older notification examples send.</li>
 * </ul>
 *
 * <p>Anything else reads as null. An unmodelled variant must not arrive as an empty instance of a
 * modelled one — a caller checking {@code instanceof CardData} would then act on an instrument that
 * was never a card.
 */
public final class ReturnDataDeserializer extends JsonDeserializer<ReturnData> {

    private static final Map<String, Class<? extends ReturnData>> BY_TYPE = Map.of(
            "cardData", CardData.class,
            "phoneData", ReturnPhoneData.class,
            "ibanData", ReturnIbanData.class,
            "walletData", ReturnWalletData.class);

    /** The nested spellings the status API uses; both mean a card. */
    private static final String[] WRAPPER_KEYS = {"cardData", "creditcardData"};

    @Override
    public ReturnData deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        if (node == null || !node.isObject()) {
            return null;
        }

        for (String key : WRAPPER_KEYS) {
            JsonNode wrapped = node.get(key);
            if (wrapped != null && wrapped.isObject()) {
                return mapper.treeToValue(withType(wrapped, key), CardData.class);
            }
        }

        JsonNode discriminator = node.get("_TYPE");
        if (discriminator != null && discriminator.isTextual()) {
            Class<? extends ReturnData> variant = BY_TYPE.get(discriminator.asText());
            return variant == null ? null : mapper.treeToValue(node, variant);
        }

        if (node.has("lastFourDigits") || node.has("cardHolder")) {
            return mapper.treeToValue(node, CardData.class);
        }
        return null;
    }

    /** The nested shape carries no {@code _TYPE}, so the wrapper key supplies the discriminator. */
    private static ObjectNode withType(JsonNode wrapped, String key) {
        ObjectNode copy = wrapped.deepCopy();
        if (!copy.has("_TYPE")) {
            copy.put("_TYPE", key);
        }
        return copy;
    }
}
