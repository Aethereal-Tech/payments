package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of a transaction response's {@code errors} array.
 *
 * <p>{@code errorCode} is the gateway's consolidated code and is what application logic should
 * branch on; {@code adapterCode} and {@code adapterMessage} come verbatim from the bank or PSP and
 * exist for diagnosis, of which there are thousands of possible values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionError(
        String errorMessage,
        int errorCode,
        String adapterMessage,
        String adapterCode) {
}
