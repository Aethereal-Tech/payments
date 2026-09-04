package net.aetherealtech.payments.event;

import java.util.Objects;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * The provider has finished retrying a failed renewal and is not going to try again. Terminal.
 *
 * <p>This is the event that separates "act" from "wait", and it is why {@link PaymentFailed} does not
 * try to carry both. A subscriber whose card failed once will very often be charged successfully on the
 * next attempt three days later; revoking their access in the meantime is a support ticket the provider
 * was about to make unnecessary. When the retries are actually done, THIS arrives, and now revoking is
 * correct.
 *
 * <p>{@link #status()} is what the provider left the subscription in — usually
 * {@link SubscriptionStatus#EXPIRED} or {@link SubscriptionStatus#CANCELLED}, and providers differ on
 * which. Both mean the same thing here: the money is not coming.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param status          the state the provider left it in
 * @param amount          what was never collected, or null when the provider reported none
 * @param attempts        how many attempts were made, or null when the provider did not say
 * @param lastPaymentRef  the provider's handle for the final failed attempt, or null
 * @param reason          free text from the provider, for a human reading a log
 */
public record DunningExhausted(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        Money amount,
        Integer attempts,
        String lastPaymentRef,
        String reason) implements PaymentEvent {

    public DunningExhausted {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
    }

    public static Builder builder(final EventHeader header, final String subscriptionRef, final SubscriptionStatus status) {
        return new Builder(header, subscriptionRef, status);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String subscriptionRef;
        private final SubscriptionStatus status;
        private Money amount;
        private Integer attempts;
        private String lastPaymentRef;
        private String reason;

        private Builder(final EventHeader header, final String subscriptionRef, final SubscriptionStatus status) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.status = status;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder attempts(final Integer attempts) {
            this.attempts = attempts;
            return this;
        }

        public Builder lastPaymentRef(final String lastPaymentRef) {
            this.lastPaymentRef = lastPaymentRef;
            return this;
        }

        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public DunningExhausted build() {
            return new DunningExhausted(header, subscriptionRef, status, amount, attempts, lastPaymentRef, reason);
        }
    }
}
