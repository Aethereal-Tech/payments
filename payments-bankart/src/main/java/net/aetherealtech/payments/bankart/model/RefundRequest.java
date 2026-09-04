package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Reverses a debit or a capture, in full or in part. {@code referenceUuid} is that transaction's UUID. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundRequest(
        String merchantTransactionId,
        String referenceUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData,
        String callbackUrl,
        String transactionToken,
        String description) {

    public RefundRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        PaymentRequest.requireText(referenceUuid, "referenceUuid");
        Objects.requireNonNull(amount, "amount");
        PaymentRequest.requireText(currency, "currency");
        AmountSerializer.requireValid(amount, "amount");
    }

    public static RefundRequest of(String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
        return new RefundRequest(merchantTransactionId, referenceUuid, amount, currency, null, null, null, null, null, null, null);
    }
}
