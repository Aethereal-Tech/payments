package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Raises or prolongs an authorization already taken by a preauthorize.
 *
 * <p>{@code referenceUuid} is that preauthorize's UUID. The amount is the INCREMENT, not the new
 * total — the scheme treats this as an additional authorization on the same instrument, which is why
 * it carries a {@code merchantTransactionId} of its own.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncrementalAuthorizationRequest(
        String merchantTransactionId,
        String referenceUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        String additionalId1,
        String additionalId2,
        String referenceSchemeLifecycleIdentifier,
        Map<String, String> extraData,
        String merchantMetaData,
        String successUrl,
        String cancelUrl,
        String errorUrl,
        String callbackUrl,
        String description,
        TransactionIndicator transactionIndicator,
        String language) {

    public IncrementalAuthorizationRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
        PaymentRequest.requireText(referenceUuid, "referenceUuid");
        Objects.requireNonNull(amount, "amount");
        PaymentRequest.requireText(currency, "currency");
        AmountSerializer.requireValid(amount, "amount");
    }

    public static IncrementalAuthorizationRequest of(
            String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
        return new IncrementalAuthorizationRequest(merchantTransactionId, referenceUuid, amount, currency,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static Builder builder(
            String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
        return new Builder(merchantTransactionId, referenceUuid, amount, currency);
    }

    public static final class Builder {
        private final String merchantTransactionId;
        private final String referenceUuid;
        private final BigDecimal amount;
        private final String currency;
        private String additionalId1;
        private String additionalId2;
        private String referenceSchemeLifecycleIdentifier;
        private Map<String, String> extraData;
        private String merchantMetaData;
        private String successUrl;
        private String cancelUrl;
        private String errorUrl;
        private String callbackUrl;
        private String description;
        private TransactionIndicator transactionIndicator;
        private String language;

        private Builder(String merchantTransactionId, String referenceUuid, BigDecimal amount, String currency) {
            this.merchantTransactionId = merchantTransactionId;
            this.referenceUuid = referenceUuid;
            this.amount = amount;
            this.currency = currency;
        }

        public Builder additionalId1(String v) { this.additionalId1 = v; return this; }
        public Builder additionalId2(String v) { this.additionalId2 = v; return this; }
        public Builder referenceSchemeLifecycleIdentifier(String v) { this.referenceSchemeLifecycleIdentifier = v; return this; }
        public Builder extraData(Map<String, String> v) { this.extraData = v; return this; }
        public Builder merchantMetaData(String v) { this.merchantMetaData = v; return this; }
        public Builder successUrl(String v) { this.successUrl = v; return this; }
        public Builder cancelUrl(String v) { this.cancelUrl = v; return this; }
        public Builder errorUrl(String v) { this.errorUrl = v; return this; }
        public Builder callbackUrl(String v) { this.callbackUrl = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder transactionIndicator(TransactionIndicator v) { this.transactionIndicator = v; return this; }
        public Builder language(String v) { this.language = v; return this; }

        public Builder redirectUrls(String successUrl, String cancelUrl, String errorUrl, String callbackUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            this.errorUrl = errorUrl;
            this.callbackUrl = callbackUrl;
            return this;
        }

        public IncrementalAuthorizationRequest build() {
            return new IncrementalAuthorizationRequest(merchantTransactionId, referenceUuid, amount, currency,
                    additionalId1, additionalId2, referenceSchemeLifecycleIdentifier, extraData, merchantMetaData,
                    successUrl, cancelUrl, errorUrl, callbackUrl, description, transactionIndicator, language);
        }
    }
}
