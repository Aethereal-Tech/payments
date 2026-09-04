package net.aetherealtech.payments.event;

import java.util.Objects;
import java.util.Optional;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A charge did not go through. ONE attempt, not the end of the road.
 *
 * <p>The distinction is why {@link DunningExhausted} exists as its own event. A first failed renewal
 * arrives here carrying {@link SubscriptionStatus#PAST_DUE}, and the provider will try again; cutting a
 * subscriber off on it would revoke access from somebody whose card is about to be retried successfully.
 * When the retries are finally done, {@code DunningExhausted} says so, and that one is terminal.
 *
 * <p>{@code subscriptionStatus} is the subscription's state AFTER this failure, and is null when the
 * payment was a one-off with no subscription behind it.
 *
 * @param header            identity, timing and acknowledgement
 * @param paymentRef        the provider's handle for the attempt; may be null when it never got one
 * @param merchantReference your own identifier, or null when the provider echoed none
 * @param amount            what was attempted
 * @param subscriptionRef   the subscription this cycle belongs to, or null for a one-off charge
 * @param subscriptionStatus the subscription's state after this failure, or null for a one-off charge
 * @param declineCode       the provider's own code; branch on this, never on {@code reason}
 * @param reason            free text from the provider, for a human reading a log
 */
public record PaymentFailed(
        EventHeader header,
        String paymentRef,
        String merchantReference,
        Money amount,
        String subscriptionRef,
        SubscriptionStatus subscriptionStatus,
        String declineCode,
        String reason) implements PaymentEvent {

    public PaymentFailed {
        Objects.requireNonNull(header, "header must not be null");
    }

    /** Present when this failure belongs to a subscription cycle. */
    public Optional<String> subscription() {
        return Optional.ofNullable(subscriptionRef);
    }

    public static Builder builder(final EventHeader header) {
        return new Builder(header);
    }

    public static final class Builder {
        private final EventHeader header;
        private String paymentRef;
        private String merchantReference;
        private Money amount;
        private String subscriptionRef;
        private SubscriptionStatus subscriptionStatus;
        private String declineCode;
        private String reason;

        private Builder(final EventHeader header) {
            this.header = header;
        }

        public Builder paymentRef(final String paymentRef) {
            this.paymentRef = paymentRef;
            return this;
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

        public Builder subscriptionStatus(final SubscriptionStatus subscriptionStatus) {
            this.subscriptionStatus = subscriptionStatus;
            return this;
        }

        public Builder declineCode(final String declineCode) {
            this.declineCode = declineCode;
            return this;
        }

        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public PaymentFailed build() {
            return new PaymentFailed(header, paymentRef, merchantReference, amount, subscriptionRef,
                    subscriptionStatus, declineCode, reason);
        }
    }
}
