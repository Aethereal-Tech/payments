package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A subscription was cancelled — by the subscriber, the merchant, or the provider.
 *
 * <p><strong>Do not revoke access on this event without reading {@link #timing()}.</strong> That is the
 * one mistake this event's shape exists to prevent. A cancellation {@link EffectiveTiming#AT_PERIOD_END}
 * leaves a subscriber owed everything they paid for up to {@link #effectiveAt()}, and cutting them off
 * on the day they clicked cancel takes back time they bought. A cancellation
 * {@link EffectiveTiming#IMMEDIATE} owes nothing from now — which is what a provider sends when it ends
 * a subscription for fraud, and treating THAT one as "at period end" keeps serving somebody who was cut
 * off on purpose.
 *
 * <p>{@link #status()} is normally {@link SubscriptionStatus#CANCELLED}, but a provider that keeps a
 * deferred cancellation {@code ACTIVE} until the date arrives reports it that way, and the adapter does
 * not overwrite it. The status is what the provider says; the timing is what to act on.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param status          its state as of this event, as the provider reported it
 * @param timing          whether it ends now or when the paid period runs out
 * @param effectiveAt     when access actually ends — the date to write down; never null
 * @param reason          the provider's or subscriber's stated reason, or null
 */
public record SubscriptionCancelled(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        EffectiveTiming timing,
        Instant effectiveAt,
        String reason) implements PaymentEvent {

    public SubscriptionCancelled {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(timing, "timing must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
    }

    /** True when the subscriber is still owed access until {@link #effectiveAt()}. */
    public boolean endsAtPeriodEnd() {
        return timing == EffectiveTiming.AT_PERIOD_END;
    }

    public static Builder builder(
            final EventHeader header,
            final String subscriptionRef,
            final SubscriptionStatus status,
            final EffectiveTiming timing,
            final Instant effectiveAt) {
        return new Builder(header, subscriptionRef, status, timing, effectiveAt);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String subscriptionRef;
        private final SubscriptionStatus status;
        private final EffectiveTiming timing;
        private final Instant effectiveAt;
        private String reason;

        private Builder(
                final EventHeader header,
                final String subscriptionRef,
                final SubscriptionStatus status,
                final EffectiveTiming timing,
                final Instant effectiveAt) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.status = status;
            this.timing = timing;
            this.effectiveAt = effectiveAt;
        }

        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public SubscriptionCancelled build() {
            return new SubscriptionCancelled(header, subscriptionRef, status, timing, effectiveAt, reason);
        }
    }
}
