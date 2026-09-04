package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A subscription reached the end of its term and did not renew.
 *
 * <p>Separate from {@link SubscriptionCancelled} because nobody decided it. A cancellation is somebody's
 * choice, arrives ahead of the date, and may still be reversible; an expiry is the date arriving. The
 * two also differ in what a consumer should offer next — win-back for the one, a renewal prompt for the
 * other.
 *
 * <p>Terminal for the entitlement: unlike {@link PaymentFailed}, no retry is coming.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param status          its state as of this event, normally {@link SubscriptionStatus#EXPIRED}
 * @param expiredAt       when the term ran out; never null
 */
public record SubscriptionExpired(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        Instant expiredAt) implements PaymentEvent {

    public SubscriptionExpired {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        if (subscriptionRef == null || subscriptionRef.isBlank()) {
            throw new IllegalArgumentException("subscriptionRef must not be blank");
        }
    }

    public static Builder builder(final EventHeader header, final String subscriptionRef, final Instant expiredAt) {
        return new Builder(header, subscriptionRef, expiredAt);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String subscriptionRef;
        private final Instant expiredAt;
        private SubscriptionStatus status = SubscriptionStatus.EXPIRED;

        private Builder(final EventHeader header, final String subscriptionRef, final Instant expiredAt) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.expiredAt = expiredAt;
        }

        /** Overrides the {@link SubscriptionStatus#EXPIRED} default, for a provider that reports otherwise. */
        public Builder status(final SubscriptionStatus status) {
            this.status = status;
            return this;
        }

        public SubscriptionExpired build() {
            return new SubscriptionExpired(header, subscriptionRef, status, expiredAt);
        }
    }
}
