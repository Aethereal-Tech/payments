package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Completes a preauthorization. {@code referenceUuid} is the preauthorize's UUID.
 *
 * <p>An amount below the authorized one is a partial capture where the adapter supports it; the
 * remainder comes back in the response's {@code extraData.remainingAmount}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaptureRequest(
        String merchantTransactionId,
        String referenceUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData,
        String description) {

    public CaptureRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        PaymentRequest.requireText(referenceUuid, "referenceUuid");
        Objects.requireNonNull(amount, "amount");
        PaymentRequest.requireText(currency, "currency");
        AmountSerializer.requireValid(amount, "amount");
    }

    public static CaptureRequest of(String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
        return new CaptureRequest(merchantTransactionId, referenceUuid, amount, currency, null, null, null, null, null);
    }
}
