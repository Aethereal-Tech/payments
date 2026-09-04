package net.aetherealtech.payments;

import java.util.Map;

/**
 * What a caller needs to start a hosted checkout, independent of which {@link PaymentProvider} serves it.
 *
 * <p>{@code merchantReference} is the caller's own order or subscription identifier, and it is the one
 * field that matters most: it is what comes back on
 * {@link net.aetherealtech.payments.event.CheckoutCompleted#merchantReference()}, so reconciling an
 * event against an order never needs a provider-specific id, and a webhook that arrives before the
 * customer's browser does — which happens, and is not an edge case — is still matchable.
 *
 * <h2>One-off or recurring</h2>
 *
 * <p>A one-off checkout carries an {@link #amount()}. A recurring one additionally carries a
 * {@link #planRef()}, a {@link #recurrence()}, or both, and which of those an adapter requires is a
 * property of the provider rather than of this record — see {@link Recurrence} for why the two providers
 * modelled here disagree. Every adapter refuses BY NAME (a
 * {@link net.aetherealtech.payments.exception.PaymentProviderException}) when it is handed a recurring
 * intent it cannot express, rather than silently selling a one-off charge.
 *
 * <p>At least one of {@code amount} and {@code planRef} must be present: with neither, there is nothing
 * to charge and nothing that knows what to charge.
 *
 * @param merchantReference the caller's own identifier for this purchase; never blank
 * @param amount            what to charge, or null when {@code planRef} carries the price
 * @param planRef           the provider's plan/product/link identifier, or null for a plain amount
 * @param recurrence        the billing cadence, or null for a one-off charge
 * @param description       what the buyer is paying for, shown on the hosted page where supported
 * @param successUrl        where the buyer's browser lands after paying
 * @param cancelUrl         where it lands if the buyer abandons the payment
 * @param errorUrl          where it lands after a failure; providers without a third URL reuse cancelUrl
 * @param callbackUrl       where the provider POSTs its webhook for THIS checkout, where supported
 * @param customer          what is known about the buyer, or null
 * @param metadata          free key/value pairs echoed back on events; never null, possibly empty
 */
public record PaymentIntent(
        String merchantReference,
        Money amount,
        String planRef,
        Recurrence recurrence,
        String description,
        String successUrl,
        String cancelUrl,
        String errorUrl,
        String callbackUrl,
        Customer customer,
        Map<String, String> metadata) {

    public PaymentIntent {
        if (merchantReference == null || merchantReference.isBlank()) {
            throw new IllegalArgumentException("merchantReference must not be blank");
        }
        if (amount == null && (planRef == null || planRef.isBlank())) {
            throw new IllegalArgumentException("a payment intent needs either an amount or a planRef");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** True when this intent asks for a subscription rather than a single charge. */
    public boolean isRecurring() {
        return recurrence != null || planRef != null;
    }

    public static Builder builder(final String merchantReference) {
        return new Builder(merchantReference);
    }

    public static final class Builder {
        private final String merchantReference;
        private Money amount;
        private String planRef;
        private Recurrence recurrence;
        private String description;
        private String successUrl;
        private String cancelUrl;
        private String errorUrl;
        private String callbackUrl;
        private Customer customer;
        private Map<String, String> metadata;

        private Builder(final String merchantReference) {
            this.merchantReference = merchantReference;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder planRef(final String planRef) {
            this.planRef = planRef;
            return this;
        }

        public Builder recurrence(final Recurrence recurrence) {
            this.recurrence = recurrence;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        /** All four at once, which is what a hosted checkout almost always needs. */
        public Builder urls(
                final String successUrl,
                final String cancelUrl,
                final String errorUrl,
                final String callbackUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            this.errorUrl = errorUrl;
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder successUrl(final String successUrl) {
            this.successUrl = successUrl;
            return this;
        }

        public Builder cancelUrl(final String cancelUrl) {
            this.cancelUrl = cancelUrl;
            return this;
        }

        public Builder errorUrl(final String errorUrl) {
            this.errorUrl = errorUrl;
            return this;
        }

        public Builder callbackUrl(final String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder customer(final Customer customer) {
            this.customer = customer;
            return this;
        }

        public Builder metadata(final Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PaymentIntent build() {
            return new PaymentIntent(merchantReference, amount, planRef, recurrence, description,
                    successUrl, cancelUrl, errorUrl, callbackUrl, customer, metadata);
        }
    }
}
