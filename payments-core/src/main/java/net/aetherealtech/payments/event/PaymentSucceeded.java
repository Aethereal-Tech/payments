package net.aetherealtech.payments.event;

import java.util.Objects;
import java.util.Optional;

import net.aetherealtech.payments.Money;

/**
 * Money moved. A charge the provider has settled or accepted as settled.
 *
 * <p>Overlaps {@link CheckoutCompleted} on purpose and is not a substitute for it: a checkout is a thing
 * the buyer did once, while a payment is a thing that also happens on every renewal, with no checkout
 * anywhere near it. A consumer fulfilling orders keys on {@code CheckoutCompleted}; one keeping a ledger
 * keys on this.
 *
 * <p>{@code subscriptionRef} is present when this payment was a subscription cycle and null when it was
 * a one-off. It is the field that tells a renewal from a purchase.
 *
 * @param header            identity, timing and acknowledgement
 * @param paymentRef        the provider's handle for the payment; never blank
 * @param merchantReference your own identifier, or null when the provider echoed none
 * @param amount            what was charged
 * @param subscriptionRef   the subscription this cycle belongs to, or null for a one-off charge
 */
public record PaymentSucceeded(
        EventHeader header,
        String paymentRef,
        String merchantReference,
        Money amount,
        String subscriptionRef) implements PaymentEvent {

    public PaymentSucceeded {
        Objects.requireNonNull(header, "header must not be null");
        if (paymentRef == null || paymentRef.isBlank()) {
            throw new IllegalArgumentException("paymentRef must not be blank");
        }
    }

    /** Present when this payment was a subscription cycle rather than a one-off charge. */
    public Optional<String> subscription() {
        return Optional.ofNullable(subscriptionRef);
    }

    public static Builder builder(final EventHeader header, final String paymentRef) {
        return new Builder(header, paymentRef);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String paymentRef;
        private String merchantReference;
        private Money amount;
        private String subscriptionRef;

        private Builder(final EventHeader header, final String paymentRef) {
            this.header = header;
            this.paymentRef = paymentRef;
        }

        public Builder merchantReference(final String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder subscriptionRef(final String subscriptionRef) {
            this.subscriptionRef = subscriptionRef;
            return this;
        }

        public PaymentSucceeded build() {
            return new PaymentSucceeded(header, paymentRef, merchantReference, amount, subscriptionRef);
        }
    }
}
