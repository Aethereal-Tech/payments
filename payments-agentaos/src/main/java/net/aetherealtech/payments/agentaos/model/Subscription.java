package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.Map;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.agentaos.AgentaOsPaymentProvider;
import net.aetherealtech.payments.agentaos.SubscriptionStatusMapping;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One subscription, as {@code /api/v1/gateway/subscriptions} lists it.
 *
 * <p>There is no endpoint that creates one of these and none that retrieves one by id — a subscription
 * begins when a buyer pays a {@link LinkType#SUBSCRIPTION} link, and reading one back means paging the
 * list.
 *
 * <p>{@link #rawStatus()} is AgentaOS's own word, kept verbatim, and {@link #status()} is what this SPI
 * makes of it. Both, because a status this adapter maps to {@link SubscriptionStatus#UNKNOWN} is only
 * actionable by an operator who can see what the gateway actually said.
 *
 * <p>Its price arrives in integer MINOR units, unlike a checkout's or a payment link's — the conversion
 * happens here rather than at a call site that cannot know which convention it is holding.
 *
 * @param id                   AgentaOS's identifier for the subscription; what reconcile and cancel key on
 * @param customerEmail        the subscriber's email, which is the only subscriber identifier on this record
 * @param customerName         the subscriber's name
 * @param planName             the plan's display name
 * @param linkId               the payment link the subscription is on; the plan's reference
 * @param billingInterval      the cadence
 * @param rawStatus            AgentaOS's status string, mirrored from Stripe
 * @param amount               the per-cycle price, converted from integer minor units
 * @param currentPeriodEnd     when the paid period ends — the date a licence gate writes down
 * @param stripeSubscriptionId the underlying Stripe subscription, for a support conversation
 * @param cancelAtPeriodEnd    whether a cancellation is booked but has not taken hold
 * @param canceledAt           when a cancellation was requested
 * @param effectiveCancelDate  when it takes or took effect
 * @param pendingPlanChange    a booked plan change, or null
 */
public record Subscription(
        String id,
        String customerEmail,
        String customerName,
        String planName,
        String linkId,
        BillingInterval billingInterval,
        String rawStatus,
        Money amount,
        Instant currentPeriodEnd,
        String stripeSubscriptionId,
        boolean cancelAtPeriodEnd,
        Instant canceledAt,
        Instant effectiveCancelDate,
        PendingPlanChange pendingPlanChange) {

    /** One subscription read from a gateway response. */
    public static Subscription from(final Map<String, Object> body) {
        final String currency = Json.string(body, "currency");
        return new Subscription(
                Json.string(body, "id"),
                Json.string(body, "customer_email"),
                Json.string(body, "customer_name"),
                Json.string(body, "plan_name"),
                Json.string(body, "link_id"),
                BillingInterval.fromWire(Json.string(body, "billing_interval")),
                Json.string(body, "status"),
                Amounts.fromMinorUnits(Json.integer(body, "unit_amount_minor"), currency,
                        AgentaOsPaymentProvider.PROVIDER_ID),
                Json.instant(body, "current_period_end"),
                Json.string(body, "stripe_subscription_id"),
                Json.bool(body, "cancel_at_period_end"),
                Json.instant(body, "canceled_at"),
                Json.instant(body, "effective_cancel_date"),
                PendingPlanChange.from(Json.object(body, "pending_plan_change"), currency));
    }

    /** This subscription's state in the SPI's vocabulary. Never null. */
    public SubscriptionStatus status() {
        return SubscriptionStatusMapping.toSpi(rawStatus);
    }

    /** The plan it is on, or null when AgentaOS named no link. */
    public Plan plan() {
        return linkId == null || linkId.isBlank() ? null : new Plan(linkId, planName);
    }

    /**
     * This subscription as the SPI's snapshot.
     *
     * <p>{@code observedAt} is the moment the gateway answered, not the moment a caller asks for it, and
     * it is what makes two snapshots comparable when a reconcile races a webhook.
     *
     * <p>The subscriber is identified by email: it is the only handle on this record, since AgentaOS's
     * customer id lives on the customers list and nothing joins the two.
     */
    public SubscriptionSnapshot toSnapshot(final Instant observedAt) {
        return SubscriptionSnapshot.builder(id, status(), observedAt)
                .plan(plan())
                .amount(amount)
                .currentPeriodEnd(currentPeriodEnd)
                .cancelAtPeriodEnd(cancelAtPeriodEnd)
                .effectiveCancelDate(effectiveCancelDate)
                .customerRef(customerEmail)
                .build();
    }
}
