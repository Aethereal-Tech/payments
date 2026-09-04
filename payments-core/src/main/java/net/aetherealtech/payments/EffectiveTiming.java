package net.aetherealtech.payments;

/**
 * When a cancellation or a plan change actually takes hold.
 *
 * <p>The distinction is the whole reason a cancellation event carries a date at all. A subscriber who
 * cancels {@link #AT_PERIOD_END} has paid for time they have not used yet and is owed access until the
 * date on the event; one cancelled {@link #IMMEDIATE}ly is owed nothing from now. A consumer that
 * treated both as "revoke now" would take away access somebody paid for, and one that treated both as
 * "revoke later" would keep serving somebody who was cut off for fraud.
 */
public enum EffectiveTiming {

    /** In force now. The date on the event is the moment it happened. */
    IMMEDIATE,

    /** In force when the current paid period ends. The date on the event is that period's end. */
    AT_PERIOD_END
}
