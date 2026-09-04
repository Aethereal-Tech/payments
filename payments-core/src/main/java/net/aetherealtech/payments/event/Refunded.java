package net.aetherealtech.payments.event;

import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * Money went back to the buyer, and this time it has actually landed.
 *
 * <p>Distinct from {@link net.aetherealtech.payments.RefundReceipt}, which is what the refund CALL
 * returned: a receipt says the provider accepted the instruction, this says the movement completed. For
 * a card scheme those can be days apart, and a refund can still fail in between. A ledger keys on this.
 *
 * <p>It also arrives for refunds this library never asked for — one issued from the provider's own
 * dashboard, or automatically inside a dispute window — which is why {@code refundRef} is the identity
 * and not something a consumer is assumed to already hold.
 *
 * @param header            identity, timing and acknowledgement
 * @param refundRef         the provider's handle for the refund; never blank
 * @param paymentRef        the payment it reverses, or null when the provider did not name one
 * @param amount            what went back, which for a partial refund is not the payment's amount
 * @param merchantReference your own identifier for the original payment, or null
 * @param reason            the stated reason, or null
 */
public record Refunded(
        EventHeader header,
        String refundRef,
        String paymentRef,
        Money amount,
        String merchantReference,
        String reason) implements PaymentEvent {

    public Refunded {
        Objects.requireNonNull(header, "header must not be null");
        if (refundRef == null || refundRef.isBlank()) {
            throw new IllegalArgumentException("refundRef must not be blank");
        }
    }

    public static Builder builder(final EventHeader header, final String refundRef) {
        return new Builder(header, refundRef);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String refundRef;
        private String paymentRef;
        private Money amount;
        private String merchantReference;
        private String reason;

        private Builder(final EventHeader header, final String refundRef) {
            this.header = header;
            this.refundRef = refundRef;
        }

        public Builder paymentRef(final String paymentRef) {
            this.paymentRef = paymentRef;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder merchantReference(final String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public Refunded build() {
            return new Refunded(header, refundRef, paymentRef, amount, merchantReference, reason);
        }
    }
}
