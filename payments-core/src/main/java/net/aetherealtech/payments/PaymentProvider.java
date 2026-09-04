package net.aetherealtech.payments;

import java.util.Set;

import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.exception.PaymentDeclinedException;
import net.aetherealtech.payments.exception.PaymentProviderException;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/**
 * One payment provider, spoken through this SPI's vocabulary rather than its own.
 *
 * <p>An implementation is an ADAPTER over a provider's client, not a replacement for it. The client
 * stays public and reachable — {@code BankartClient} still exposes preauthorize, capture, void, payout
 * and the schedule API — and a caller who has deliberately chosen one gateway reaches for it directly.
 * What this interface buys is the code that must not know which gateway it is: a checkout controller, a
 * webhook endpoint, a nightly reconcile.
 *
 * <h2>Not every provider does everything</h2>
 *
 * <p>{@link #capabilities()} is the whole answer to that, and it is a question to ASK rather than to
 * discover. Calling an operation whose {@link ProviderCapability} is not declared raises an
 * {@link UnsupportedCapabilityException} naming the capability — never a null, never a no-op, and never
 * a plausible-looking result that quietly did nothing.
 *
 * <h2>What every implementation promises</h2>
 *
 * <ul>
 *   <li><strong>Nothing provider-shaped escapes.</strong> Every native exception is translated at this
 *       boundary into the {@link net.aetherealtech.payments.exception.PaymentException} family, so a
 *       caller can catch one type and be done. A caller reaching below the SPI accepts the gateway's
 *       own exceptions along with its own richer types; that trade is the point of the layering.</li>
 *   <li><strong>Verification is not optional.</strong> {@link #handleWebhook(InboundWebhook)} verifies
 *       before it parses, and an unverifiable request is a {@link WebhookVerificationException} — never
 *       a parsed event with a warning attached.</li>
 *   <li><strong>Thread-safe.</strong> Build one and share it. Implementations hold an HTTP client and
 *       configuration, both expensive to create and safe to reuse.</li>
 * </ul>
 */
public interface PaymentProvider {

    /**
     * A stable, lower-case identifier for this provider — {@code "bankart"}, {@code "agentaos"}.
     *
     * <p>It is what a registry keys on, what every event's {@link
     * net.aetherealtech.payments.event.EventHeader#provider()} carries, and what appears on every
     * exception this adapter raises, so a log line says who refused without the reader guessing.
     */
    String id();

    /**
     * What this provider can do. Immutable, constant for the life of the instance, never null.
     *
     * <p>Constant because the catalogue is a property of the ADAPTER — which operations it implements —
     * rather than of the merchant account behind it. An account not entitled to refunds still gets
     * {@link ProviderCapability#REFUND} here and a refusal from the provider at call time, because the
     * alternative is a probe request on startup against a live payments API.
     */
    Set<ProviderCapability> capabilities();

    /** Whether {@link #capabilities()} contains {@code capability}. */
    default boolean supports(final ProviderCapability capability) {
        return capabilities().contains(capability);
    }

    /**
     * Starts a hosted checkout and returns somewhere to send the buyer.
     *
     * <p><strong>Persist {@link RedirectTarget#checkoutRef()} against your order before redirecting.</strong>
     * The webhook that decides whether the payment happened travels independently of the buyer's browser
     * and may well arrive first.
     *
     * <p>An {@code intent} that {@link PaymentIntent#isRecurring() is recurring} needs
     * {@link ProviderCapability#RECURRING_CHECKOUT}; the adapter refuses by name rather than quietly
     * selling a one-off charge instead of a subscription.
     *
     * @throws PaymentDeclinedException      if the provider refused the payment itself
     * @throws PaymentProviderException      if the request was rejected, or the call failed — check
     *                                       {@link PaymentProviderException#outcomeUnknown()} before
     *                                       retrying anything
     * @throws UnsupportedCapabilityException if this provider cannot serve this kind of intent
     */
    RedirectTarget startCheckout(PaymentIntent intent);

    /**
     * Verifies an inbound webhook and turns it into one {@link PaymentEvent}.
     *
     * <p>Verification happens FIRST and unconditionally. A request that does not authenticate is a
     * {@link WebhookVerificationException} whose {@link WebhookVerificationException#reason()} names the
     * component that failed, and nothing in the payload has been read, let alone acted on.
     *
     * <p>A webhook this SPI has no event for is a
     * {@link net.aetherealtech.payments.event.UnknownEvent} carrying the raw payload — a verified fact
     * we cannot interpret is still a fact, and dropping it would lose an audit trail. Answer the request
     * with {@link PaymentEvent#acknowledgement()}, and only after the event is durably recorded:
     * acknowledging first turns a crash into a payment you never hear about again.
     *
     * @throws WebhookVerificationException if the request did not authenticate
     */
    PaymentEvent handleWebhook(InboundWebhook webhook);

    /**
     * The provider's current view of one subscription, asked rather than waited for.
     *
     * <p>Needs {@link ProviderCapability#RECONCILE}. This is the answer to webhooks not being a complete
     * history — a provider that documents three events and fires eight, an endpoint that was down for an
     * afternoon, an event that arrived out of order. A licence gate that polls this stays correct
     * without trusting delivery.
     *
     * @param subscriptionRef the provider's identifier for the subscription
     * @throws PaymentProviderException       if the provider refused or the call failed
     * @throws UnsupportedCapabilityException if this provider cannot be polled
     */
    SubscriptionSnapshot reconcile(String subscriptionRef);

    /**
     * Cancels a subscription and returns its state afterwards.
     *
     * <p>{@code atPeriodEnd} needs {@link ProviderCapability#CANCEL_AT_PERIOD_END}; a provider that can
     * only cancel immediately refuses it rather than cancelling immediately and calling it done, which
     * would take away access somebody has paid for.
     *
     * <p>The returned snapshot carries {@link SubscriptionSnapshot#effectiveCancelDate()} — the date
     * access is owed until. Read it; do not assume "now".
     *
     * @param subscriptionRef the provider's identifier for the subscription
     * @param atPeriodEnd     true to let the paid period run out, false to end it now
     * @throws PaymentProviderException       if the provider refused or the call failed
     * @throws UnsupportedCapabilityException if this provider has no subscriptions, or cannot defer
     */
    SubscriptionSnapshot cancelSubscription(String subscriptionRef, boolean atPeriodEnd);

    /**
     * Refunds a payment, in whole or in part.
     *
     * <p>Needs {@link ProviderCapability#REFUND}. A refund is a new movement of money rather than an
     * undo: it has its own reference, its own timing, and — for a card scheme — its own settlement,
     * which is why it answers with a {@link RefundReceipt} rather than {@code void}.
     *
     * @throws PaymentProviderException       if the provider refused or the call failed
     * @throws UnsupportedCapabilityException if this provider exposes no refund API
     */
    RefundReceipt refund(RefundRequest request);
}
