package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of a status response's {@code errors} array.
 *
 * <p>Deliberately not {@link TransactionError}: the status API names the same two concepts
 * {@code message} and {@code code}, and its example shows {@code code} as a JSON string where the
 * transaction API sends {@code errorCode} as a number. Modelling them as one type would mean
 * papering over a difference the wire actually has.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusError(
        String message,
        String code,
        String adapterMessage,
        String adapterCode) {
}
