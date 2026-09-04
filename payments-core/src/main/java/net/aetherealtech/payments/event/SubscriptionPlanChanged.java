package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * A subscription moved from one plan to another.
 *
 * <p><strong>Deliberately carries no money.</strong> An upgrade's financial consequence is a proration
 * credit, a charge, an adjusted next invoice, or nothing at all, decided by the provider under rules
 * this SPI does not model — and a number on this event would be read as "what the customer was charged"
 * whichever of those it actually was. The money arrives as its own {@link PaymentSucceeded}. What this
 * event states is the entitlement change, which is the part a consumer must act on.
 *
 * <p>{@link #timing()} says whether the new plan is in force now or at the end of the paid period, and
 * {@link #effectiveAt()} gives the date. A downgrade is normally
 * {@link EffectiveTiming#AT_PERIOD_END} — applying it immediately would take away capability the
 * subscriber has already paid for.
 *
 * @param header          identity, timing and acknowledgement
 * @param subscriptionRef the provider's identifier for the subscription; never blank
 * @param status          its state as of this event
 * @param previousPlan    the plan it was on; never null
 * @param newPlan         the plan it is moving to; never null
 * @param timing          whether the change is in force now or at the end of the paid period
 * @param effectiveAt     when the new plan takes hold; never null
 */
public record SubscriptionPlanChanged(
        EventHeader header,
        String subscriptionRef,
        SubscriptionStatus status,
        Plan previousPlan,
        Plan newPlan,
        EffectiveTiming timing,
        Instant effectiveAt) implements PaymentEvent {

    public SubscriptionPlanChanged {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(previousPlan, "previousPlan must not be null");
        Objects.requireNonNull(newPlan, "newPlan must not be null");
        Objects.requireNonNull(timing, "timing must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
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
        private Plan previousPlan;
        private Plan newPlan;
        private EffectiveTiming timing;
        private Instant effectiveAt;

        private Builder(final EventHeader header, final String subscriptionRef, final SubscriptionStatus status) {
            this.header = header;
            this.subscriptionRef = subscriptionRef;
            this.status = status;
        }

        public Builder from(final Plan previousPlan) {
            this.previousPlan = previousPlan;
            return this;
        }

        public Builder to(final Plan newPlan) {
            this.newPlan = newPlan;
            return this;
        }

        public Builder effective(final EffectiveTiming timing, final Instant effectiveAt) {
            this.timing = timing;
            this.effectiveAt = effectiveAt;
            return this;
        }

        public SubscriptionPlanChanged build() {
            return new SubscriptionPlanChanged(header, subscriptionRef, status, previousPlan, newPlan, timing, effectiveAt);
        }
    }
}
