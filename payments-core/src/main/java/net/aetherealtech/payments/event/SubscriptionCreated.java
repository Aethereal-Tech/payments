package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A subscription now exists. The first one of these is what turns a checkout into a recurring
 * relationship.
 *
 * <p>It does NOT mean money has moved. A subscription created into a trial arrives with
 * {@link SubscriptionStatus#TRIALING} and nothing charged; one whose first payment has not landed yet
 * arrives {@link SubscriptionStatus#INCOMPLETE}. Read {@link #status()} rather than assuming the
 * existence of the subscription implies a paid one — {@link PaymentSucceeded} says that.
 *
 * <p>{@code currentPeriodEnd} is the date a licence gate writes down. It is null only when the provider
 * has not booked a first period yet, which goes with an {@code INCOMPLETE} status.
 *
 * @param header            identity, timing and acknowledgement
 * @param subscriptionRef   the provider's identifier for the subscription; never blank
 * @param status            its state as of this event
 * @param plan              the plan it is on, or null when the provider named none
 * @param amount            the per-cycle price, or null when the provider reported none
 * @param currentPeriodEnd  when the current paid period ends, or null before the first has booked
 * @param trialEndsAt       when a trial ends, or null when there is none
 * @param customerRef       the provider's identifier for the subscriber, or null
 * @param merchantReference your own identifier from the intent, or null when none was echoed
 */
public record SubscriptionCreated(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        Plan plan,
        Money amount,
        Instant currentPeriodEnd,
        Instant trialEndsAt,
        String customerRef,
        String merchantReference) implements PaymentEvent {

    public SubscriptionCreated {
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
        private Plan plan;
        private Money amount;
        private Instant currentPeriodEnd;
        private Instant trialEndsAt;
        private String customerRef;
        private String merchantReference;

        private Builder(final EventHeader header, final String subscriptionRef, final SubscriptionStatus status) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.status = status;
        }

        public Builder plan(final Plan plan) {
            this.plan = plan;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder currentPeriodEnd(final Instant currentPeriodEnd) {
            this.currentPeriodEnd = currentPeriodEnd;
            return this;
        }

        public Builder trialEndsAt(final Instant trialEndsAt) {
            this.trialEndsAt = trialEndsAt;
            return this;
        }

        public Builder customerRef(final String customerRef) {
            this.customerRef = customerRef;
            return this;
        }

        public Builder merchantReference(final String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public SubscriptionCreated build() {
            return new SubscriptionCreated(header, subscriptionRef, status, plan, amount,
                    currentPeriodEnd, trialEndsAt, customerRef, merchantReference);
        }
    }
}
