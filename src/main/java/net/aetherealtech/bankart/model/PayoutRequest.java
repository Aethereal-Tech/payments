package net.aetherealtech.bankart.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Credits the customer's account.
 *
 * <p>The destination comes from either a {@code referenceUuid} (a stored instrument) or a
 * {@code transactionToken}; the docs require at least one, which the constructor enforces rather
 * than leaving to a round trip.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayoutRequest(
        String merchantTransactionId,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        String referenceUuid,
        String transactionToken,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData,
        String callbackUrl,
        String description,
        Customer customer) {

    public PayoutRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        Objects.requireNonNull(amount, "amount");
        PaymentRequest.requireText(currency, "currency");
        AmountSerializer.requireValid(amount, "amount");
        boolean hasReference = referenceUuid != null && !referenceUuid.isBlank();
        boolean hasToken = transactionToken != null && !transactionToken.isBlank();
        if (!hasReference && !hasToken) {
            throw new IllegalArgumentException("a payout needs either referenceUuid or transactionToken");
        }
    }

    public static PayoutRequest toReference(String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
        return new PayoutRequest(merchantTransactionId, amount, currency, referenceUuid, null, null, null, null, null, null, null, null);
    }
}
