package net.aetherealtech.payments.event;

import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * A buyer disputed a payment with their bank, and the money has been taken back pending the outcome.
 *
 * <p>An AUDIT fact. It says nothing about the subscription's status and must not be turned into one: a
 * chargeback can be reversed ({@link ChargebackReversed}), and a consumer that cancelled a subscription
 * on this event has no clean way back. If the provider decides the subscription should end, it says so
 * with a {@link SubscriptionCancelled}; act on that.
 *
 * <p>It is also the one event nobody triggered from the merchant's side — it arrives days or months
 * after the payment, initiated by the scheme, and the payment it names may be long since fulfilled.
 *
 * @param header            identity, timing and acknowledgement
 * @param paymentRef        the payment being disputed; never blank
 * @param chargebackRef     the provider's handle for the dispute itself, or null
 * @param amount            what was taken back, which need not be the payment's full amount
 * @param reasonCode        the scheme's reason code; branch on this, never on {@code reason}
 * @param reason            free text, for a human reading a log
 * @param merchantReference your own identifier for the payment, or null
 */
public record ChargebackOpened(
        EventHeader header,
        String paymentRef,
        String chargebackRef,
        Money amount,
        String reasonCode,
        String reason,
        String merchantReference) implements PaymentEvent {

    public ChargebackOpened {
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
        private String reasonCode;
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

        public Builder reasonCode(final String reasonCode) {
            this.reasonCode = reasonCode;
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

        public ChargebackOpened build() {
            return new ChargebackOpened(header, paymentRef, chargebackRef, amount, reasonCode, reason, merchantReference);
        }
    }
}
