package net.aetherealtech.payments;

/**
 * A subscription's state, in one vocabulary regardless of which provider reported it.
 *
 * <p>This is the enum a consumer gates access on, so each adapter documents its own mapping from the
 * provider's spelling — see each adapter's status-mapping table under {@code openspec/specs/} — and none of
 * them invents a value.
 *
 * <p>{@link #UNKNOWN} is the one addition to the seven states a consumer asked for, and it exists so an
 * unrecognised provider status has somewhere to land that is not a wrong answer. A provider adding a
 * status is a thing that happens; mapping it to the nearest neighbour would silently grant or revoke
 * access on a guess, and refusing to parse would drop a webhook that has already been acknowledged.
 * A consumer meeting {@code UNKNOWN} should leave its own state alone and alert.
 */
public enum SubscriptionStatus {

    /** Paid and current. */
    ACTIVE,

    /** Inside a free trial; no money has changed hands yet, but access is owed. */
    TRIALING,

    /** A renewal payment failed and the provider is still retrying. Access is a product decision. */
    PAST_DUE,

    /** Ended by somebody's decision — the subscriber's, the merchant's, or the provider's. */
    CANCELLED,

    /** Ended by reaching its term without renewing, or by exhausted retries. */
    EXPIRED,

    /** Suspended and resumable; billing is stopped, the subscription is not over. */
    PAUSED,

    /** Created but never successfully paid for the first time — the first charge has not landed. */
    INCOMPLETE,

    /** The provider reported a status this SPI does not model. Act on nothing; alert somebody. */
    UNKNOWN
}
