package net.aetherealtech.payments.bankart.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Deletes a stored instrument. {@code referenceUuid} is the UUID of the register,
 * debit-with-register or preauthorize-with-register that created it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeregisterRequest(
        String merchantTransactionId,
        String referenceUuid,
        TokenType tokenType,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData) {

    public DeregisterRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        PaymentRequest.requireText(referenceUuid, "referenceUuid");
    }

    public static DeregisterRequest of(String merchantTransactionId, String referenceUuid) {
        return new DeregisterRequest(merchantTransactionId, referenceUuid, null, null, null, null, null);
    }
}
