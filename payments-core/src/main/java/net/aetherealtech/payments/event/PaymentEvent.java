package net.aetherealtech.payments.event;

import java.time.Instant;

/**
 * Something a provider told us happened, in one vocabulary regardless of who told us.
 *
 * <p>Sealed, so a consumer can switch over it exhaustively and have the compiler point at the branch a
 * new event type needs. That is deliberately a source-compatible-but-not-binary-compatible promise:
 * adding an event here will make an exhaustive switch stop compiling until it is handled, which is the
 * warning a payments consumer wants rather than a silent default branch.
 *
 * <h2>Handling these safely</h2>
 *
 * <p>Delivery is at-least-once and unordered. Two properties follow, and neither is optional:
 *
 * <ol>
 *   <li><strong>Idempotent on {@link EventHeader#eventId()}.</strong> The same event arrives more than
 *       once — that is what a retry schedule does when an acknowledgement is slow or lost.</li>
 *   <li><strong>Able to move backwards as well as forwards.</strong> An event with an
 *       {@link EventHeader#occurredAt()} older than the state you hold is a late redelivery, not a
 *       contradiction; and a transaction going from failed to successful for the same reference is a
 *       documented gateway behaviour. Let the newest fact win.</li>
 * </ol>
 *
 * <p>Where those two are not enough — a provider that documents few subscription events, or an endpoint
 * that was down — {@link net.aetherealtech.payments.PaymentProvider#reconcile(String)} answers with the
 * current truth rather than a history.
 *
 * <h2>Why the list looks the way it does</h2>
 *
 * <p>{@link PaymentFailed} and {@link DunningExhausted} are two events rather than one because a failed
 * payment alone cannot express the difference between a first attempt and the end of all retries — and
 * those two call for opposite actions. A first failed renewal is a {@code PaymentFailed} carrying
 * {@link net.aetherealtech.payments.SubscriptionStatus#PAST_DUE}; the end of the road is a
 * {@code DunningExhausted}, and it is terminal.
 *
 * <p>{@link ChargebackOpened} and {@link ChargebackReversed} are audit facts. The state a consumer gates
 * access on moves through a subscription status transition, not through these.
 *
 * <p>{@link UnknownEvent} exists so that a provider adding an event type is a thing a running consumer
 * survives. It carries the raw payload and nothing else, because pretending to have parsed something
 * unrecognised is worse than saying so.
 */
public sealed interface PaymentEvent
        permits CheckoutCompleted, PaymentSucceeded, PaymentFailed,
                SubscriptionCreated, SubscriptionRenewed, SubscriptionPlanChanged,
                SubscriptionCancelled, SubscriptionExpired, DunningExhausted,
                TrialStarted, TrialEnding,
                Refunded, ChargebackOpened, ChargebackReversed, UnknownEvent {

    /** Identity, provenance, timing, acknowledgement text and the raw payload. Never null. */
    EventHeader header();

    /** Shorthand for {@code header().eventId()} — what to dedupe on. */
    default String eventId() {
        return header().eventId();
    }

    /** Shorthand for {@code header().occurredAt()} — what to order on. */
    default Instant occurredAt() {
        return header().occurredAt();
    }

    /** Shorthand for {@code header().acknowledgement()} — the exact body to answer the request with. */
    default String acknowledgement() {
        return header().acknowledgement();
    }
}
