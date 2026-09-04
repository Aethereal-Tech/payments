package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * What to POST to {@code /api/v1/gateway/payment-links} to create one.
 *
 * <p>A {@link LinkType#SUBSCRIPTION} link needs a {@link #billingInterval()} — refused here rather than
 * by the server, because the refusal can say which field is missing.
 *
 * @param amount          the price; never null
 * @param description     what the buyer is paying for
 * @param name            the link's display name, which becomes the subscription's plan name
 * @param imageUrl        an image for the hosted page
 * @param webhookUrl      where AgentaOS POSTs events for sessions opened against this link
 * @param successUrl      where the buyer lands after paying
 * @param cancelUrl       where the buyer lands after abandoning
 * @param metadata        key/value pairs echoed back
 * @param expiresAt       when the link stops accepting buyers
 * @param taxRateId       the organization's tax rate to apply
 * @param checkoutFields  extra questions for the hosted page
 * @param type            one charge or a subscription; null takes the server's default
 * @param billingInterval the cadence; required when {@code type} is {@link LinkType#SUBSCRIPTION}
 * @param trialPeriodDays a free trial's length in days, 1–730, for a subscription link
 */
public record CreatePaymentLinkRequest(
        Money amount,
        String description,
        String name,
        String imageUrl,
        String webhookUrl,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        Instant expiresAt,
        String taxRateId,
        List<CheckoutField> checkoutFields,
        LinkType type,
        BillingInterval billingInterval,
        Integer trialPeriodDays) {

    /** The shortest trial AgentaOS accepts. */
    public static final int MIN_TRIAL_DAYS = 1;

    /** The longest trial AgentaOS accepts. */
    public static final int MAX_TRIAL_DAYS = 730;

    public CreatePaymentLinkRequest {
        Objects.requireNonNull(amount, "amount must not be null");
        if (type == LinkType.SUBSCRIPTION && billingInterval == null) {
            throw new IllegalArgumentException("a subscription payment link needs a billingInterval");
        }
        if (trialPeriodDays != null && (trialPeriodDays < MIN_TRIAL_DAYS || trialPeriodDays > MAX_TRIAL_DAYS)) {
            throw new IllegalArgumentException("trialPeriodDays must be between " + MIN_TRIAL_DAYS + " and "
                    + MAX_TRIAL_DAYS + ", got " + trialPeriodDays);
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        checkoutFields = checkoutFields == null ? List.of() : List.copyOf(checkoutFields);
    }

    /** A link that takes one payment. */
    public static Builder oneTime(final Money amount) {
        return new Builder(amount).type(LinkType.ONE_TIME);
    }

    /** A link that starts a subscription when a buyer pays it. */
    public static Builder subscription(final Money amount, final BillingInterval billingInterval) {
        return new Builder(amount).type(LinkType.SUBSCRIPTION).billingInterval(billingInterval);
    }

    /** The request body's fields, camelCase and in a fixed order. */
    public Map<String, Object> toWire() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("amount", amount.amount());
        out.put("currency", amount.currency());
        out.put("description", description);
        out.put("name", name);
        out.put("imageUrl", imageUrl);
        out.put("webhookUrl", webhookUrl);
        out.put("successUrl", successUrl);
        out.put("cancelUrl", cancelUrl);
        out.put("metadata", metadata.isEmpty() ? null : metadata);
        out.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        out.put("taxRateId", taxRateId);
        out.put("checkoutFields", checkoutFields.isEmpty()
                ? null
                : checkoutFields.stream().map(CheckoutField::toWire).toList());
        out.put("type", type == null ? null : type.wireValue());
        out.put("billingInterval", billingInterval == null ? null : billingInterval.wireValue());
        out.put("trialPeriodDays", trialPeriodDays);
        return out;
    }

    /** Builds a {@link CreatePaymentLinkRequest}. */
    public static final class Builder {

        private final Money amount;
        private String description;
        private String name;
        private String imageUrl;
        private String webhookUrl;
        private String successUrl;
        private String cancelUrl;
        private Map<String, String> metadata;
        private Instant expiresAt;
        private String taxRateId;
        private List<CheckoutField> checkoutFields;
        private LinkType type;
        private BillingInterval billingInterval;
        private Integer trialPeriodDays;

        private Builder(final Money amount) {
            this.amount = amount;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder imageUrl(final String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder webhookUrl(final String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        public Builder urls(final String successUrl, final String cancelUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            return this;
        }

        public Builder metadata(final Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder expiresAt(final Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder taxRateId(final String taxRateId) {
            this.taxRateId = taxRateId;
            return this;
        }

        public Builder checkoutFields(final List<CheckoutField> checkoutFields) {
            this.checkoutFields = checkoutFields;
            return this;
        }

        public Builder type(final LinkType type) {
            this.type = type;
            return this;
        }

        public Builder billingInterval(final BillingInterval billingInterval) {
            this.billingInterval = billingInterval;
            return this;
        }

        public Builder trialPeriodDays(final Integer trialPeriodDays) {
            this.trialPeriodDays = trialPeriodDays;
            return this;
        }

        public CreatePaymentLinkRequest build() {
            return new CreatePaymentLinkRequest(amount, description, name, imageUrl, webhookUrl, successUrl,
                    cancelUrl, metadata, expiresAt, taxRateId, checkoutFields, type, billingInterval,
                    trialPeriodDays);
        }
    }
}
