package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.Map;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.agentaos.AgentaOsPaymentProvider;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * A plan change that has been agreed but has not taken hold yet.
 *
 * <p>Its presence on a subscription is the whole difference between a {@code subscription.updated} this
 * adapter can name and one it cannot: a downgrade is booked now and applied at {@link #effectiveAt()},
 * and until then the subscriber keeps what they paid for.
 *
 * @param linkId      the payment link the subscription is moving to; the new plan's reference
 * @param planName    that link's display name, or null
 * @param amount      the new per-cycle price, converted from integer minor units
 * @param effectiveAt when the new plan takes over
 */
public record PendingPlanChange(String linkId, String planName, Money amount, Instant effectiveAt) {

    /** One pending change read from a subscription's {@code pending_plan_change}. */
    public static PendingPlanChange from(final Map<String, Object> body, final String currency) {
        if (body == null) {
            return null;
        }
        return new PendingPlanChange(
                Json.string(body, "link_id"),
                Json.string(body, "plan_name"),
                Amounts.fromMinorUnits(Json.integer(body, "unit_amount_minor"), currency,
                        AgentaOsPaymentProvider.PROVIDER_ID),
                Json.instant(body, "effective_at"));
    }

    /** The plan being moved to, or null when the payload named no link. */
    public Plan asPlan() {
        return linkId == null || linkId.isBlank() ? null : new Plan(linkId, planName);
    }
}
