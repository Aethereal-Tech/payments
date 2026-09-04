package net.aetherealtech.payments.bankart.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Asks an adapter for a list it publishes — the banks behind an online-banking method, typically.
 *
 * <p>There is no identifier in the body. What is being asked for is the {@code optionsName} PATH
 * segment, and {@code parameters} is a free-form bag whose accepted keys are the adapter's business,
 * not the API's: the schema types it as a bare {@code object} with nothing inside.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OptionsRequest(Map<String, Object> parameters) {

    /** No parameters at all, which is what an adapter that needs none expects. */
    public static OptionsRequest empty() {
        return new OptionsRequest(Map.of());
    }

    public static OptionsRequest of(Map<String, Object> parameters) {
        return new OptionsRequest(parameters);
    }
}
