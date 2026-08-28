package net.aetherealtech.bankart.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The body of a debit or a preauthorize.
 *
 * <p>One record serves both because the documented field lists are identical but for
 * {@code captureInMinutes}, which preauthorize alone accepts —
 * {@link net.aetherealtech.bankart.BankartClient#debit} rejects a request that sets it rather than
 * quietly sending a field the endpoint does not take.
 *
 * <p>Set {@code transactionToken} for a payment.js-tokenised card, {@code referenceUuid} for a
 * charge against a previously registered instrument, or neither for a hosted-page checkout.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRequest(
        String merchantTransactionId,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal surchargeAmount,
        String captureInMinutes,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData,
        String referenceUuid,
        String successUrl,
        String cancelUrl,
        String errorUrl,
        String callbackUrl,
        String transactionToken,
        String description,
        Boolean withRegister,
        TransactionIndicator transactionIndicator,
        Customer customer,
        ThreeDSecureData threeDSecureData,
        String language,
        Boolean includeTracing) {

    public PaymentRequest {
        requireText(merchantTransactionId, "merchantTransactionId");
        Objects.requireNonNull(amount, "amount");
        requireText(currency, "currency");
        AmountSerializer.requireValid(amount, "amount");
        AmountSerializer.requireValid(surchargeAmount, "surchargeAmount");
    }

    public static Builder builder(String merchantTransactionId, BigDecimal amount, String currency) {
        return new Builder(merchantTransactionId, amount, currency);
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public static final class Builder {
        private final String merchantTransactionId;
        private final BigDecimal amount;
        private final String currency;
        private BigDecimal surchargeAmount;
        private String captureInMinutes;
        private String additionalId1;
        private String additionalId2;
        private Map<String, String> extraData;
        private String merchantMetaData;
        private String referenceUuid;
        private String successUrl;
        private String cancelUrl;
        private String errorUrl;
        private String callbackUrl;
        private String transactionToken;
        private String description;
        private Boolean withRegister;
        private TransactionIndicator transactionIndicator;
        private Customer customer;
        private ThreeDSecureData threeDSecureData;
        private String language;
        private Boolean includeTracing;

        private Builder(String merchantTransactionId, BigDecimal amount, String currency) {
            this.merchantTransactionId = merchantTransactionId;
            this.amount = amount;
            this.currency = currency;
        }

        public Builder surchargeAmount(BigDecimal v) { this.surchargeAmount = v; return this; }
        public Builder captureInMinutes(String v) { this.captureInMinutes = v; return this; }
        public Builder additionalId1(String v) { this.additionalId1 = v; return this; }
        public Builder additionalId2(String v) { this.additionalId2 = v; return this; }
        public Builder extraData(Map<String, String> v) { this.extraData = v; return this; }
        public Builder merchantMetaData(String v) { this.merchantMetaData = v; return this; }
        public Builder referenceUuid(String v) { this.referenceUuid = v; return this; }
        public Builder successUrl(String v) { this.successUrl = v; return this; }
        public Builder cancelUrl(String v) { this.cancelUrl = v; return this; }
        public Builder errorUrl(String v) { this.errorUrl = v; return this; }
        public Builder callbackUrl(String v) { this.callbackUrl = v; return this; }
        public Builder transactionToken(String v) { this.transactionToken = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder withRegister(Boolean v) { this.withRegister = v; return this; }
        public Builder transactionIndicator(TransactionIndicator v) { this.transactionIndicator = v; return this; }
        public Builder customer(Customer v) { this.customer = v; return this; }
        public Builder threeDSecureData(ThreeDSecureData v) { this.threeDSecureData = v; return this; }
        public Builder language(String v) { this.language = v; return this; }
        public Builder includeTracing(Boolean v) { this.includeTracing = v; return this; }

        /** All four redirect targets at once, which is what a hosted-page checkout always needs. */
        public Builder redirectUrls(String successUrl, String cancelUrl, String errorUrl, String callbackUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            this.errorUrl = errorUrl;
            this.callbackUrl = callbackUrl;
            return this;
        }

        public PaymentRequest build() {
            return new PaymentRequest(merchantTransactionId, amount, currency, surchargeAmount, captureInMinutes,
                    additionalId1, additionalId2, extraData, merchantMetaData, referenceUuid, successUrl, cancelUrl,
                    errorUrl, callbackUrl, transactionToken, description, withRegister, transactionIndicator,
                    customer, threeDSecureData, language, includeTracing);
        }
    }
}
