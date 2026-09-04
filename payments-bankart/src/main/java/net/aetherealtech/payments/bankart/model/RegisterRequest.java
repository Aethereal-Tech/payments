package net.aetherealtech.bankart.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Stores a customer's payment instrument without charging it. The resulting {@code uuid} is what
 * later debits pass as {@code referenceUuid}.
 *
 * <p>The alternative is a debit carrying {@code withRegister}, which charges and stores in one step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterRequest(
        String merchantTransactionId,
        String additionalId1,
        String additionalId2,
        Map<String, String> extraData,
        String merchantMetaData,
        String successUrl,
        String cancelUrl,
        String errorUrl,
        String callbackUrl,
        String transactionToken,
        String description,
        Customer customer,
        ThreeDSecureData threeDSecureData,
        String language) {

    public RegisterRequest {
        PaymentRequest.requireText(merchantTransactionId, "merchantTransactionId");
    }

    public static Builder builder(String merchantTransactionId) {
        return new Builder(merchantTransactionId);
    }

    public static final class Builder {
        private final String merchantTransactionId;
        private String additionalId1;
        private String additionalId2;
        private Map<String, String> extraData;
        private String merchantMetaData;
        private String successUrl;
        private String cancelUrl;
        private String errorUrl;
        private String callbackUrl;
        private String transactionToken;
        private String description;
        private Customer customer;
        private ThreeDSecureData threeDSecureData;
        private String language;

        private Builder(String merchantTransactionId) {
            this.merchantTransactionId = merchantTransactionId;
        }

        public Builder additionalId1(String v) { this.additionalId1 = v; return this; }
        public Builder additionalId2(String v) { this.additionalId2 = v; return this; }
        public Builder extraData(Map<String, String> v) { this.extraData = v; return this; }
        public Builder merchantMetaData(String v) { this.merchantMetaData = v; return this; }
        public Builder successUrl(String v) { this.successUrl = v; return this; }
        public Builder cancelUrl(String v) { this.cancelUrl = v; return this; }
        public Builder errorUrl(String v) { this.errorUrl = v; return this; }
        public Builder callbackUrl(String v) { this.callbackUrl = v; return this; }
        public Builder transactionToken(String v) { this.transactionToken = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder customer(Customer v) { this.customer = v; return this; }
        public Builder threeDSecureData(ThreeDSecureData v) { this.threeDSecureData = v; return this; }
        public Builder language(String v) { this.language = v; return this; }

        public Builder redirectUrls(String successUrl, String cancelUrl, String errorUrl, String callbackUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            this.errorUrl = errorUrl;
            this.callbackUrl = callbackUrl;
            return this;
        }

        public RegisterRequest build() {
            return new RegisterRequest(merchantTransactionId, additionalId1, additionalId2, extraData,
                    merchantMetaData, successUrl, cancelUrl, errorUrl, callbackUrl, transactionToken,
                    description, customer, threeDSecureData, language);
        }
    }
}
