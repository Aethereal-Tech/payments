package net.aetherealtech.payments.event;

import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * A dispute was decided in the merchant's favour and the money came back.
 *
 * <p>The other half of {@link ChargebackOpened}, and the reason that one must not be acted on as a state
 * change. Both are audit facts; between them the money left and returned, and a consumer's ledger is the
 * only place that difference belongs.
 *
 * <p>It may arrive without this library ever having seen the corresponding {@code ChargebackOpened} — a
 * dispute opened before an integration went live, or an event lost while an endpoint was down. Handle it
 * on its own terms rather than assuming a prior record exists.
 *
 * @param header            identity, timing and acknowledgement
 * @param paymentRef        the payment the dispute was about; never blank
 * @param chargebackRef     the provider's handle for the dispute being reversed, or null
 * @param amount            what came back
 * @param reason            free text, for a human reading a log
 * @param merchantReference your own identifier for the payment, or null
 */
public record ChargebackReversed(
        EventHeader header,
        String paymentRef,
        String chargebackRef,
        Money amount,
        String reason,
        String merchantReference) implements PaymentEvent {

    public ChargebackReversed {
        Objects.requireNonNull(header, "header must not be null");
        if (paymentRef == null || paymentRef.isBlank()) {
            throw new IllegalArgumentException("paymentRef must not be blank");
        }
    }

    public static Builder builder(final EventHeader header, final String paymentRef) {
        return new Builder(header, paymentRef);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String paymentRef;
        private String chargebackRef;
        private Money amount;
        private String reason;
        private String merchantReference;

        private Builder(final EventHeader header, final String paymentRef) {
            this.header = header;
            this.paymentRef = paymentRef;
        }

        public Builder chargebackRef(final String chargebackRef) {
            this.chargebackRef = chargebackRef;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public Builder merchantReference(final String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public ChargebackReversed build() {
            return new ChargebackReversed(header, paymentRef, chargebackRef, amount, reason, merchantReference);
        }
    }
}
