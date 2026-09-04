package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A free trial began. Access is owed; no money has moved.
 *
 * <p>Optional in practice — a provider that reports a trial only through
 * {@link SubscriptionCreated}'s {@link SubscriptionCreated#status()} of
 * {@link SubscriptionStatus#TRIALING} never emits this, and a consumer that handles the status
 * correctly needs nothing more. It is modelled because a provider that DOES send a distinct trial event
 * would otherwise be forced into {@link UnknownEvent}, which is a worse answer than a small record.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param trialEndsAt     when the trial ends and billing begins; never null
 * @param plan            the plan being trialled, or null when the provider named none
 */
public record TrialStarted(
        EventHeader header,
        String subscriptionRef,
        Instant trialEndsAt,
        Plan plan) implements PaymentEvent {

    public TrialStarted {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(trialEndsAt, "trialEndsAt must not be null");
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
    }

    public static Builder builder(final EventHeader header, final String subscriptionRef, final Instant trialEndsAt) {
        return new Builder(header, subscriptionRef, trialEndsAt);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String subscriptionRef;
        private final Instant trialEndsAt;
        private Plan plan;

        private Builder(final EventHeader header, final String subscriptionRef, final Instant trialEndsAt) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.trialEndsAt = trialEndsAt;
        }

        public Builder plan(final Plan plan) {
            this.plan = plan;
            return this;
        }

        public TrialStarted build() {
            return new TrialStarted(header, subscriptionRef, trialEndsAt, plan);
        }
    }
}
