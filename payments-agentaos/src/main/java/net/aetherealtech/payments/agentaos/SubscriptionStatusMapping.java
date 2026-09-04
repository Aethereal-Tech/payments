package net.aetherealtech.payments.agentaos;

import java.util.Locale;

import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.SubscriptionStatus;

/**
 * AgentaOS's subscription status, in this SPI's vocabulary.
 *
 * <p>AgentaOS mirrors Stripe's status verbatim — {@code types.ts} says so — so this table is really a
 * reading of Stripe's subscription lifecycle:
 *
 * <table>
 *   <caption>The whole mapping</caption>
 *   <tr><th>AgentaOS</th><th>SPI</th><th>why</th></tr>
 *   <tr><td>{@code incomplete}</td><td>{@link SubscriptionStatus#INCOMPLETE}</td>
 *       <td>created, first charge not landed</td></tr>
 *   <tr><td>{@code incomplete_expired}</td><td>{@link SubscriptionStatus#EXPIRED}</td>
 *       <td>the first charge never landed and the window closed</td></tr>
 *   <tr><td>{@code trialing}</td><td>{@link SubscriptionStatus#TRIALING}</td><td>access owed, nothing charged</td></tr>
 *   <tr><td>{@code active}</td><td>{@link SubscriptionStatus#ACTIVE}</td><td>paid and current</td></tr>
 *   <tr><td>{@code past_due}</td><td>{@link SubscriptionStatus#PAST_DUE}</td><td>a renewal failed, retries continue</td></tr>
 *   <tr><td>{@code canceled}</td><td>{@link SubscriptionStatus#CANCELLED}</td>
 *       <td>ended by a decision — note the single-l American spelling on the wire</td></tr>
 *   <tr><td>{@code unpaid}</td><td>{@link SubscriptionStatus#EXPIRED}</td>
 *       <td>the END of dunning: retries are done and the invoice was never paid. Not
 *           {@code CANCELLED}, because nobody decided anything — the term simply ran out
 *           unpaid, which is exactly what {@code EXPIRED} means here</td></tr>
 *   <tr><td>{@code paused}</td><td>{@link SubscriptionStatus#PAUSED}</td><td>stopped and resumable</td></tr>
 *   <tr><td>anything else</td><td>{@link SubscriptionStatus#UNKNOWN}</td>
 *       <td>a status added since this was written; act on nothing, alert somebody</td></tr>
 * </table>
 */
public final class SubscriptionStatusMapping {

    private SubscriptionStatusMapping() {
    }

    /** The SPI status for an AgentaOS one. {@link SubscriptionStatus#UNKNOWN} for null or unrecognised. */
    public static SubscriptionStatus toSpi(final String rawStatus) {
        if (rawStatus == null) {
            return SubscriptionStatus.UNKNOWN;
        }
        return switch (rawStatus.trim().toLowerCase(Locale.ROOT)) {
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.EXPIRED;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELLED;
            case "unpaid" -> SubscriptionStatus.EXPIRED;
            case "paused" -> SubscriptionStatus.PAUSED;
            default -> SubscriptionStatus.UNKNOWN;
        };
    }

    /**
     * Whether a {@code subscription.payment_failed} carrying this status means the retries are over.
     *
     * <p>This decides between two SPI events that call for opposite actions. A failure that leaves the
     * subscription {@code past_due} is one attempt of several and must not revoke anybody's access; one
     * that leaves it {@code unpaid} or {@code canceled} is the end of the road, and a consumer that
     * waits for a further event will wait forever, because there is no further event.
     *
     * <p>The payload's own status is the only signal available — AgentaOS sends no attempt counter and
     * no "final attempt" flag — and it is a good one, since Stripe moves a subscription out of
     * {@code past_due} precisely when its dunning settings say to give up.
     */
    @Provisional("types.ts calls the field \"Stripe subscription status, mirrored verbatim\" but states "
            + "nothing about which statuses mean retries have stopped; unpaid and canceled are read as "
            + "terminal from Stripe's own dunning model, not from anything in packages/pay/src.")
    public static boolean dunningIsOver(final String rawStatus) {
        final SubscriptionStatus status = toSpi(rawStatus);
        return status == SubscriptionStatus.EXPIRED || status == SubscriptionStatus.CANCELLED;
    }
}
