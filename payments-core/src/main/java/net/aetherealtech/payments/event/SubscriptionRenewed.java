package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A subscription rolled into a new paid period.
 *
 * <p>The field that matters is {@link #currentPeriodEnd()}: it is the new expiry, and writing it down is
 * the entire response to this event. A consumer that computes the next period itself — "add a month to
 * the old one" — drifts from the provider on every proration, retry, dunning recovery and month of
 * unequal length, and the drift shows up as a licence that expires a day early.
 *
 * <p>Pairs with a {@link PaymentSucceeded} carrying the same {@code subscriptionRef}: this is the
 * subscription fact, that is the money fact, and a provider may send either, both, or them in either
 * order.
 *
 * @param header           identity, timing and acknowledgement
 * @param subscriptionRef  the provider's identifier for the subscription; never blank
 * @param status           its state as of this event, normally {@link SubscriptionStatus#ACTIVE}
 * @param currentPeriodEnd when the newly-paid period ends — the new expiry; never null
 * @param plan             the plan it renewed on, or null when the provider named none
 * @param amount           what was charged for this cycle, or null when the provider reported none
 * @param paymentRef       the provider's handle for the renewal payment, or null
 */
public record SubscriptionRenewed(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        Plan plan,
        Money amount,
        String paymentRef) implements PaymentEvent {

    public SubscriptionRenewed {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentPeriodEnd, "currentPeriodEnd must not be null");
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
    }

    public static Builder builder(
            final EventHeader header,
            final String subscriptionRef,
            final SubscriptionStatus status,
            final Instant currentPeriodEnd) {
        return new Builder(header, subscriptionRef, status, currentPeriodEnd);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String subscriptionRef;
        private final SubscriptionStatus status;
        private final Instant currentPeriodEnd;
        private Plan plan;
        private Money amount;
        private String paymentRef;

        private Builder(
                final EventHeader header,
                final String subscriptionRef,
                final SubscriptionStatus status,
                final Instant currentPeriodEnd) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.status = status;
            this.currentPeriodEnd = currentPeriodEnd;
        }

        public Builder plan(final Plan plan) {
            this.plan = plan;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder paymentRef(final String paymentRef) {
            this.paymentRef = paymentRef;
            return this;
        }

        public SubscriptionRenewed build() {
            return new SubscriptionRenewed(header, subscriptionRef, status, currentPeriodEnd, plan, amount, paymentRef);
        }
    }
}
