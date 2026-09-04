package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.Map;

import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.agentaos.SubscriptionStatusMapping;
import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * What {@code POST /api/v1/gateway/subscriptions/&#123;id&#125;/cancel} answers with.
 *
 * <p>Four fields, and none of them is the plan or the price — a cancellation tells you when access ends,
 * not what was being sold. {@link #effectiveCancelDate()} is the date to write down.
 *
 * @param rawStatus           AgentaOS's status string after the cancellation
 * @param currentPeriodEnd    when the paid period ends
 * @param cancelAtPeriodEnd   whether the cancellation was deferred to that date
 * @param effectiveCancelDate when access actually ends
 */
public record CancelSubscriptionResult(
        String rawStatus,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant effectiveCancelDate) {

    /** One result read from a gateway response. */
    public static CancelSubscriptionResult from(final Map<String, Object> body) {
        return new CancelSubscriptionResult(
                Json.string(body, "status"),
                Json.instant(body, "current_period_end"),
                Json.bool(body, "cancel_at_period_end"),
                Json.instant(body, "effective_cancel_date"));
    }

    /** The state AgentaOS left the subscription in, in the SPI's vocabulary. Never null. */
    public SubscriptionStatus status() {
        return SubscriptionStatusMapping.toSpi(rawStatus);
    }

    /** This result as the SPI's snapshot of the subscription it names. */
    public SubscriptionSnapshot toSnapshot(final String subscriptionRef, final Instant observedAt) {
        return SubscriptionSnapshot.builder(subscriptionRef, status(), observedAt)
                .currentPeriodEnd(currentPeriodEnd)
                .cancelAtPeriodEnd(cancelAtPeriodEnd)
                .effectiveCancelDate(effectiveCancelDate)
                .build();
    }
}
