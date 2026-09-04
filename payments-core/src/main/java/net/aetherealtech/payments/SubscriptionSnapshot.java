package net.aetherealtech.payments;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A subscription as the provider sees it right now, from {@link PaymentProvider#reconcile(String)}.
 *
 * <p>This exists because webhooks are not a complete history. A provider that documents three events and
 * fires eight, or a merchant whose endpoint was down for an afternoon, leaves a consumer's own record
 * wrong in a way no amount of careful event handling fixes. Polling this on a schedule — and after any
 * event that arrives out of order — is how a licence gate stays correct without trusting delivery.
 *
 * <p>{@code observedAt} is what makes two snapshots comparable and is set by the ADAPTER at the moment
 * the provider answered, not by the caller. A consumer holding a newer snapshot should ignore an older
 * one, which is also the answer to a reconcile racing a webhook.
 *
 * @param subscriptionRef     the provider's identifier for the subscription; never blank
 * @param status              its state now
 * @param plan                the plan it is on, or null when the provider did not name one
 * @param amount              the per-cycle price, or null when the provider did not report one
 * @param currentPeriodEnd    when the paid period ends — what a licence gate writes down — or null
 *                            before the first cycle has booked
 * @param cancelAtPeriodEnd   true when a cancellation is scheduled but has not taken hold
 * @param effectiveCancelDate when a scheduled or completed cancellation takes or took effect, else null
 * @param trialEndsAt         when a trial ends, or null when there is none
 * @param customerRef         the provider's identifier for the subscriber, or null
 * @param observedAt          when the provider answered; never null
 */
public record SubscriptionSnapshot(
        String subscriptionRef,
        SubscriptionStatus status,
        Plan plan,
        Money amount,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant effectiveCancelDate,
        Instant trialEndsAt,
        String customerRef,
        Instant observedAt) {

    public SubscriptionSnapshot {
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    /** What a licence gate would write down, when the provider has told us. */
    public Optional<Instant> activeUntil() {
        return Optional.ofNullable(currentPeriodEnd);
    }

    public static Builder builder(final String subscriptionRef, final SubscriptionStatus status, final Instant observedAt) {
        return new Builder(subscriptionRef, status, observedAt);
    }

    public static final class Builder {
        private final String subscriptionRef;
        private final SubscriptionStatus status;
        private final Instant observedAt;
        private Plan plan;
        private Money amount;
        private Instant currentPeriodEnd;
        private boolean cancelAtPeriodEnd;
        private Instant effectiveCancelDate;
        private Instant trialEndsAt;
        private String customerRef;

        private Builder(final String subscriptionRef, final SubscriptionStatus status, final Instant observedAt) {
            this.subscriptionRef = subscriptionRef;
            this.status = status;
            this.observedAt = observedAt;
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

        public Builder cancelAtPeriodEnd(final boolean cancelAtPeriodEnd) {
            this.cancelAtPeriodEnd = cancelAtPeriodEnd;
            return this;
        }

        public Builder effectiveCancelDate(final Instant effectiveCancelDate) {
            this.effectiveCancelDate = effectiveCancelDate;
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

        public SubscriptionSnapshot build() {
            return new SubscriptionSnapshot(subscriptionRef, status, plan, amount, currentPeriodEnd,
                    cancelAtPeriodEnd, effectiveCancelDate, trialEndsAt, customerRef, observedAt);
        }
    }
}
