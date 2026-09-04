package net.aetherealtech.payments.agentaos.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One extra question the hosted checkout page asks the buyer.
 *
 * <p>The answers come back in the checkout's metadata, keyed by {@link #key()}.
 *
 * @param key         the metadata key the answer is stored under; never blank
 * @param label       what the buyer sees above the input
 * @param type        which control to render
 * @param required    whether the buyer may leave it empty
 * @param placeholder greyed-out hint text, or null
 * @param options     the choices for a {@link CheckoutFieldType#SELECT}; empty otherwise
 */
public record CheckoutField(
        String key,
        String label,
        CheckoutFieldType type,
        boolean required,
        String placeholder,
        List<String> options) {

    public CheckoutField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("a checkout field's key must not be blank");
        }
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** A required free-text question. */
    public static CheckoutField text(final String key, final String label) {
        return new CheckoutField(key, label, CheckoutFieldType.TEXT, true, null, List.of());
    }

    /** One field as the gateway returned it. */
    public static CheckoutField from(final Map<String, Object> body) {
        return new CheckoutField(
                Json.string(body, "key"),
                Json.string(body, "label"),
                CheckoutFieldType.fromWire(Json.string(body, "type")),
                Json.bool(body, "required"),
                Json.string(body, "placeholder"),
                Json.array(body, "options").stream().map(String::valueOf).toList());
    }

    /** This field as a request body fragment — camelCase, like every request this adapter sends. */
    public Map<String, Object> toWire() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("label", label);
        out.put("type", type == null ? null : type.wireValue());
        out.put("required", required);
        out.put("placeholder", placeholder);
        out.put("options", options.isEmpty() ? null : options);
        return out;
    }
}
