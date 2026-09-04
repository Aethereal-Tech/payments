package net.aetherealtech.bankart.model;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Cancels a preauthorization. Amount and currency are optional and only meaningful for a partial
 * void, which not every adapter supports.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoidRequest(
        String merchantTransactionId,
        String referenceUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData) {

    public VoidRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        PaymentRequest.requireText(referenceUuid, "referenceUuid");
        AmountSerializer.requireValid(amount, "amount");
    }

    public static VoidRequest of(String merchantTransactionId, String referenceUuid) {
        return new VoidRequest(merchantTransactionId, referenceUuid, null, null, null, null, null, null);
    }
}
