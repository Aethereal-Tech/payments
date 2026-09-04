package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * A trial is about to end and the first real charge is coming.
 *
 * <p>A warning, not a state change: nothing about the subscription has moved, and a consumer that acts
 * on it at all acts by telling the subscriber. Several jurisdictions require exactly that notice before
 * a trial converts, which is the reason this is modelled rather than dropped as noise.
 *
 * <p>{@link #trialEndsAt()} is the date to put in the message. {@link #amount()} is what will be
 * charged, where the provider said so.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param trialEndsAt     when the trial ends and billing begins; never null
 * @param amount          what will be charged then, or null when the provider did not say
 */
public record TrialEnding(
        EventHeader header,
        String subscriptionRef,
        Instant trialEndsAt,
        Money amount) implements PaymentEvent {

    public TrialEnding {
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
        private Money amount;

        private Builder(final EventHeader header, final String subscriptionRef, final Instant trialEndsAt) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.trialEndsAt = trialEndsAt;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public TrialEnding build() {
            return new TrialEnding(header, subscriptionRef, trialEndsAt, amount);
        }
    }
}
