package net.aetherealtech.payments.agentaos;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.PaymentProvider;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundReceipt;
import net.aetherealtech.payments.RefundRequest;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;
import net.aetherealtech.payments.agentaos.model.BillingInterval;
import net.aetherealtech.payments.agentaos.model.Checkout;
import net.aetherealtech.payments.agentaos.model.CreateCheckoutRequest;
import net.aetherealtech.payments.agentaos.model.Page;
import net.aetherealtech.payments.agentaos.model.Subscription;
import net.aetherealtech.payments.event.CheckoutCompleted;
import net.aetherealtech.payments.event.DunningExhausted;
import net.aetherealtech.payments.event.EventHeader;
import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.event.PaymentFailed;
import net.aetherealtech.payments.event.SubscriptionCancelled;
import net.aetherealtech.payments.event.SubscriptionCreated;
import net.aetherealtech.payments.event.SubscriptionPlanChanged;
import net.aetherealtech.payments.event.SubscriptionRenewed;
import net.aetherealtech.payments.event.UnknownEvent;
import net.aetherealtech.payments.exception.PaymentProviderException;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/**
 * AgentaOS, spoken through this SPI.
 *
 * <h2>What it can and cannot do</h2>
 *
 * <p>It has no refund. Not "an undocumented one", not "one behind a flag" — there is no refund, reverse
 * or payment-reversal call anywhere in the SDK, and the changelog describes cancellation as explicitly
 * not refunding. {@link #refund(RefundRequest)} therefore refuses by name rather than inventing an
 * endpoint to call, and {@link ProviderCapability#REFUND} is absent from {@link #capabilities()}.
 *
 * <p>It also stores no instrument a caller can charge later ({@link ProviderCapability#TOKENIZATION} is
 * absent): recurring charges happen because a subscription exists, not because a token does.
 *
 * <h2>Subscriptions arrive sideways</h2>
 *
 * <p>There is no call that creates a subscription. One begins when a buyer pays a payment link of type
 * {@code subscription}, so a recurring {@link PaymentIntent} must name that link as its
 * {@link PaymentIntent#planRef()}; a bare {@link net.aetherealtech.payments.Recurrence} cannot be served
 * and is refused by name. See {@link #startCheckout(PaymentIntent)}.
 *
 * <h2>Event identity</h2>
 *
 * <p>AgentaOS's payloads carry no event id, so every event's {@link EventHeader#eventId()} is
 * synthesised — {@link AgentaOsEventId} states the rule exactly. Its {@code occurredAt} is the
 * timestamp inside the signature header, which is the only time AgentaOS states about a delivery.
 * {@link PaymentEvent#acknowledgement()} is the empty string: AgentaOS wants a 2xx and does not read
 * the body.
 */
public final class AgentaOsPaymentProvider implements PaymentProvider {

    /** The id every event, exception and log line from this adapter carries. */
    public static final String PROVIDER_ID = "agentaos";

    /**
     * The metadata key a caller's own order reference travels in.
     *
     * <p>AgentaOS has no merchant-reference field. Metadata is the only channel from a checkout back to
     * the webhook that reports it paid, so the reference rides in there and is read out again on
     * {@link CheckoutCompleted#merchantReference()}. It stays in
     * {@link CheckoutCompleted#metadata()} as well, because that map is what the provider returned and
     * editing it would misreport what was sent.
     */
    @Provisional("CreateCheckoutParams and CheckoutCompletedData each declare a metadata map in "
            + "packages/pay/src/types.ts, but nothing in packages/pay/src shows the one arriving in the "
            + "other; a merchant reference riding in metadata rests on that assumed round trip.")
    public static final String MERCHANT_REFERENCE_KEY = "merchantReference";

    /** AgentaOS wants a 2xx and does not read the response body. */
    private static final String ACKNOWLEDGEMENT = "";

    private static final int MAX_RECONCILE_PAGES = 100;

    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.HOSTED_CHECKOUT,
            ProviderCapability.RECURRING_CHECKOUT,
            ProviderCapability.MACHINE_PAYMENT_URL,
            ProviderCapability.WEBHOOK_SIGNATURE,
            ProviderCapability.SUBSCRIPTIONS,
            ProviderCapability.CANCEL_AT_PERIOD_END,
            ProviderCapability.RECONCILE,
            ProviderCapability.PLAN_CHANGE);

    private final AgentaOsClient client;
    private final AgentaOsWebhookVerifier verifier;
    private final Clock clock;

    /**
     * The usual construction: a client and a verifier built from one configuration.
     *
     * <p>A configuration with no webhook secret yields no verifier, and
     * {@link #handleWebhook(InboundWebhook)} then refuses every request rather than reading one it
     * cannot authenticate. An integration that only opens checkouts and polls needs no secret, and
     * failing at startup for the want of one it will never use would be a puzzle with no cause.
     */
    public AgentaOsPaymentProvider(final AgentaOsConfig config) {
        this(new AgentaOsClient(Objects.requireNonNull(config, "config must not be null")),
                config.verifiesWebhooks()
                        ? new AgentaOsWebhookVerifier(config.webhookSecret(), config.webhookTolerance())
                        : null,
                Clock.systemUTC());
    }

    public AgentaOsPaymentProvider(
            final AgentaOsClient client, final AgentaOsWebhookVerifier verifier, final Clock clock) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.verifier = verifier;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return CAPABILITIES;
    }

    /** The underlying client, for the calls this SPI has no vocabulary for. */
    public AgentaOsClient client() {
        return client;
    }

    // ---------------------------------------------------------------- checkout

    /**
     * Opens a hosted checkout.
     *
     * <p>A one-off intent becomes a link-less session carrying its own amount and currency. A RECURRING
     * intent becomes a session against the payment link its {@link PaymentIntent#planRef()} names — and
     * needs one, because AgentaOS creates a subscription only when a buyer pays a subscription link.
     * An intent that carries a {@link net.aetherealtech.payments.Recurrence} and no {@code planRef} is
     * refused with a message saying to create the link first; anything else would sell a single charge
     * where a subscription was asked for.
     *
     * <p>A {@code Recurrence} that IS supplied alongside a link is checked against what AgentaOS can
     * express — monthly or yearly, nothing else — without a round trip. Whether it matches that
     * particular link's own interval is the server's to enforce; asking would put a second request on
     * the buyer's critical path to duplicate a check that happens anyway.
     *
     * <p>{@link PaymentIntent#errorUrl()} is dropped: AgentaOS has two return URLs, not three.
     */
    @Override
    public RedirectTarget startCheckout(final PaymentIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        final CreateCheckoutRequest.Builder builder = intent.isRecurring()
                ? recurring(intent)
                : CreateCheckoutRequest.of(intent.amount());

        final Map<String, String> metadata = new LinkedHashMap<>(intent.metadata());
        metadata.put(MERCHANT_REFERENCE_KEY, intent.merchantReference());

        builder.description(intent.description())
                .metadata(metadata)
                .webhookUrl(intent.callbackUrl())
                .urls(intent.successUrl(), intent.cancelUrl());
        if (intent.customer() != null) {
            builder.buyerEmail(intent.customer().email())
                    .buyerName(intent.customer().name())
                    .buyerCountry(intent.customer().countryCode())
                    .buyerVat(intent.customer().vatNumber());
        }

        final Checkout checkout = client.createCheckout(builder.build(), intent.merchantReference());
        if (checkout.checkoutUrl() == null || checkout.checkoutUrl().isBlank()) {
            throw new PaymentProviderException(
                    "AgentaOS opened session " + checkout.sessionId() + " but returned no checkout_url,"
                            + " so there is nowhere to send the buyer.",
                    PROVIDER_ID, "malformed_response", false, null);
        }
        return new RedirectTarget(checkout.checkoutUrl(), false, checkout.x402Url(), checkout.sessionId());
    }

    // ---------------------------------------------------------------- webhooks

    /**
     * Verifies an inbound webhook and turns it into one event.
     *
     * <p>The mapping, and why each one:
     *
     * <ul>
     *   <li>{@code checkout.session.completed} → {@link CheckoutCompleted}.</li>
     *   <li>{@code subscription.created} → {@link SubscriptionCreated};
     *       {@code subscription.renewed} → {@link SubscriptionRenewed}.</li>
     *   <li>{@code subscription.payment_failed} → {@link PaymentFailed} carrying
     *       {@link SubscriptionStatus#PAST_DUE} while retries continue, but
     *       {@link DunningExhausted} once the payload's own status has gone terminal
     *       ({@code unpaid} or {@code canceled}). The payload carries no attempt counter, so its status
     *       is the only signal that says the retries have stopped — and acting on the wrong one either
     *       revokes access from somebody whose card is about to work, or keeps serving somebody who is
     *       never going to pay. See {@link SubscriptionStatusMapping#dunningIsOver(String)}.</li>
     *   <li>{@code subscription.canceled} → {@link SubscriptionCancelled}, its timing read from
     *       {@code cancelAtPeriodEnd} and its date from {@code effectiveCancelDate}, then
     *       {@code currentPeriodEnd}, then the signature's own timestamp.</li>
     *   <li>{@code subscription.updated} → {@link SubscriptionPlanChanged} when a {@code pendingPlan} is
     *       present and names a link, and {@link UnknownEvent} otherwise. The event says only that
     *       SOMETHING changed; without a pending plan it might be the payment method, the quantity or a
     *       cancellation being withdrawn, and turning any of those into a plan change would put a
     *       fabricated entitlement into a consumer's ledger.</li>
     *   <li>{@code send.completed} and {@code send.failed} → {@link UnknownEvent}. These are the wallet
     *       rail: money leaving the merchant's own wallet, not a buyer paying them. Mapping them to
     *       {@link net.aetherealtech.payments.event.PaymentSucceeded} or {@link PaymentFailed} would
     *       record an inbound payment for money that went the other way. The event is kept, with its
     *       type and its raw payload, so a consumer that does run that rail can still see it.</li>
     *   <li>Anything else → {@link UnknownEvent} with the raw payload.</li>
     * </ul>
     *
     * <p>A payload that is not JSON, or that is missing the identifier its event needs, also becomes an
     * {@link UnknownEvent}: it authenticated, so it is a fact worth keeping, and there is nothing
     * honest to turn it into.
     */
    @Override
    public PaymentEvent handleWebhook(final InboundWebhook webhook) {
        Objects.requireNonNull(webhook, "webhook must not be null");
        if (verifier == null) {
            throw new WebhookVerificationException(
                    "AgentaOS webhook rejected: no webhook secret is configured, so nothing can be verified.",
                    PROVIDER_ID, "no_webhook_secret");
        }
        final long signedAt = verifier.verify(webhook);
        final Instant occurredAt = Instant.ofEpochSecond(signedAt);
        final String raw = webhook.bodyAsString();

        final Map<String, Object> body;
        try {
            body = Json.parseObject(raw);
        } catch (Json.SyntaxException e) {
            return new UnknownEvent(header(null, null, signedAt, occurredAt, raw), null);
        }
        final String rawType = Json.string(body, "type");
        // The webhook body never passes through the SDK's HTTP layer, so it is not snake-cased the way a
        // REST response is and types.ts names its fields in camelCase. Aliasing both spellings is how
        // this survives being wrong about which; the aliases stop at the top level, so a merchant's own
        // metadata keys are left exactly as they were sent.
        final Map<String, Object> data = Json.withCamelAliases(Json.object(body, "data"));
        final AgentaOsEventType type = AgentaOsEventType.fromWire(rawType);

        return switch (type) {
            case CHECKOUT_SESSION_COMPLETED -> checkoutCompleted(data, rawType, signedAt, occurredAt, raw);
            case SUBSCRIPTION_CREATED -> subscriptionCreated(data, rawType, signedAt, occurredAt, raw);
            case SUBSCRIPTION_RENEWED -> subscriptionRenewed(data, rawType, signedAt, occurredAt, raw);
            case SUBSCRIPTION_PAYMENT_FAILED -> paymentFailed(data, rawType, signedAt, occurredAt, raw);
            case SUBSCRIPTION_CANCELED -> subscriptionCancelled(data, rawType, signedAt, occurredAt, raw);
            case SUBSCRIPTION_UPDATED -> subscriptionUpdated(data, rawType, signedAt, occurredAt, raw);
            case SEND_COMPLETED, SEND_FAILED ->
                    unknown(rawType, Json.string(data, "transactionId"), signedAt, occurredAt, raw);
            case UNKNOWN -> unknown(rawType, null, signedAt, occurredAt, raw);
        };
    }

    // ---------------------------------------------------------------- subscriptions

    /**
     * The gateway's current view of one subscription.
     *
     * <p>Implemented by paging {@code GET /api/v1/gateway/subscriptions} until the id turns up, because
     * <strong>there is no retrieve endpoint</strong> — the SDK's subscriptions resource has
     * {@code list}, {@code cancel}, {@code invoices} and the plan-change pair, and nothing that fetches
     * one subscription by id. A retrieve may well exist on the server; guessing its path here would mean
     * a 404 at reconcile time on an integration that had passed every test.
     *
     * @throws PaymentProviderException with code {@code not_found} when no page contains the id
     */
    @Provisional("packages/pay/src/resources/subscriptions.ts declares list, cancel, invoices, "
            + "previewPlanChange and changePlan and no retrieve, so this pages the list; whether the "
            + "server exposes GET /api/v1/gateway/subscriptions/{id} is unknown.")
    @Override
    public SubscriptionSnapshot reconcile(final String subscriptionRef) {
        requireRef(subscriptionRef, "subscriptionRef");
        final int pageSize = client.config().pageSize();
        int offset = 0;
        for (int page = 0; page < MAX_RECONCILE_PAGES; page++) {
            final Page<Subscription> current = client.listSubscriptions(pageSize, offset);
            for (final Subscription subscription : current.items()) {
                if (subscriptionRef.equals(subscription.id())) {
                    return subscription.toSnapshot(clock.instant());
                }
            }
            if (!current.hasMore() || current.items().isEmpty()) {
                throw notFound(subscriptionRef);
            }
            offset += current.items().size();
        }
        // has_more that never turns false would otherwise page forever against a live payments API.
        throw new PaymentProviderException(
                "AgentaOS still reported more subscriptions after " + MAX_RECONCILE_PAGES
                        + " pages while looking for \"" + subscriptionRef + "\"; giving up rather than paging"
                        + " indefinitely.",
                PROVIDER_ID, "reconcile_exhausted", false, null);
    }

    /**
     * Cancels a subscription, now or at the end of the paid period.
     *
     * <p>The returned snapshot's {@link SubscriptionSnapshot#effectiveCancelDate()} is the date access is
     * owed until. It carries no plan and no price: the cancel response states neither, and inventing them
     * from a second request would report a plan that may have changed since.
     */
    @Override
    public SubscriptionSnapshot cancelSubscription(final String subscriptionRef, final boolean atPeriodEnd) {
        requireRef(subscriptionRef, "subscriptionRef");
        return client.cancelSubscription(subscriptionRef, atPeriodEnd)
                .toSnapshot(subscriptionRef, clock.instant());
    }

    /**
     * Always refuses: AgentaOS has no refund API.
     *
     * @throws UnsupportedCapabilityException always
     */
    @Override
    public RefundReceipt refund(final RefundRequest request) {
        throw new UnsupportedCapabilityException(PROVIDER_ID, ProviderCapability.REFUND);
    }

    // ---------------------------------------------------------------- event mapping

    private PaymentEvent checkoutCompleted(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String sessionId = Json.string(data, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return unknown(rawType, null, signedAt, occurredAt, raw);
        }
        final Map<String, String> metadata = Json.stringMap(data, "metadata");
        return CheckoutCompleted.builder(header(rawType, sessionId, signedAt, occurredAt, raw), sessionId)
                .merchantReference(metadata.get(MERCHANT_REFERENCE_KEY))
                // The amount is a STRING here, where the REST resources send a decimal number.
                .amount(Amounts.fromMajorUnits(Json.string(data, "amount"), Json.string(data, "currency")))
                // The on-chain transaction is the only payment handle a completed checkout carries, and
                // a card ("mor") settlement has none at all.
                .paymentRef(Json.string(data, "txHash"))
                .metadata(metadata)
                .build();
    }

    private PaymentEvent subscriptionCreated(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String id = Json.string(data, "id");
        if (id == null || id.isBlank()) {
            return unknown(rawType, null, signedAt, occurredAt, raw);
        }
        return SubscriptionCreated.builder(
                        header(rawType, id, signedAt, occurredAt, raw),
                        id,
                        SubscriptionStatusMapping.toSpi(Json.string(data, "status")))
                .plan(planOf(data))
                .amount(webhookAmount(data))
                .currentPeriodEnd(Json.instant(data, "currentPeriodEnd"))
                // No trial end is reported. currentPeriodEnd on a trialing subscription is very probably
                // it, but "very probably" is not what a licence gate should be told.
                .customerRef(Json.string(data, "customerEmail"))
                .build();
    }

    private PaymentEvent subscriptionRenewed(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String id = Json.string(data, "id");
        final Instant currentPeriodEnd = Json.instant(data, "currentPeriodEnd");
        // The new expiry IS the event. Substituting a computed date would write a wrong one into a
        // licence gate, so a renewal that does not state it stays an unknown fact rather than a wrong one.
        if (id == null || id.isBlank() || currentPeriodEnd == null) {
            return unknown(rawType, id, signedAt, occurredAt, raw);
        }
        return SubscriptionRenewed.builder(
                        header(rawType, id, signedAt, occurredAt, raw),
                        id,
                        SubscriptionStatusMapping.toSpi(Json.string(data, "status")),
                        currentPeriodEnd)
                .plan(planOf(data))
                .amount(webhookAmount(data))
                .build();
    }

    private PaymentEvent paymentFailed(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String id = Json.string(data, "id");
        if (id == null || id.isBlank()) {
            return unknown(rawType, null, signedAt, occurredAt, raw);
        }
        final String status = Json.string(data, "status");
        final EventHeader header = header(rawType, id, signedAt, occurredAt, raw);
        if (SubscriptionStatusMapping.dunningIsOver(status)) {
            return DunningExhausted.builder(header, id, SubscriptionStatusMapping.toSpi(status))
                    .amount(webhookAmount(data))
                    .build();
        }
        return PaymentFailed.builder(header)
                .subscriptionRef(id)
                .subscriptionStatus(SubscriptionStatusMapping.toSpi(status))
                .amount(webhookAmount(data))
                .build();
    }

    private PaymentEvent subscriptionCancelled(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String id = Json.string(data, "id");
        if (id == null || id.isBlank()) {
            return unknown(rawType, null, signedAt, occurredAt, raw);
        }
        final boolean atPeriodEnd = Json.bool(data, "cancelAtPeriodEnd");
        Instant effectiveAt = Json.instant(data, "effectiveCancelDate");
        if (effectiveAt == null && atPeriodEnd) {
            effectiveAt = Json.instant(data, "currentPeriodEnd");
        }
        if (effectiveAt == null) {
            effectiveAt = occurredAt;
        }
        return SubscriptionCancelled.builder(
                        header(rawType, id, signedAt, occurredAt, raw),
                        id,
                        SubscriptionStatusMapping.toSpi(Json.string(data, "status")),
                        atPeriodEnd ? EffectiveTiming.AT_PERIOD_END : EffectiveTiming.IMMEDIATE,
                        effectiveAt)
                .build();
    }

    private PaymentEvent subscriptionUpdated(
            final Map<String, Object> data, final String rawType, final long signedAt,
            final Instant occurredAt, final String raw) {
        final String id = Json.string(data, "id");
        final Map<String, Object> pending = Json.withCamelAliases(Json.object(data, "pendingPlan"));
        final String currentLink = Json.string(data, "linkId");
        final String newLink = pending == null ? null : Json.string(pending, "linkId");
        if (id == null || id.isBlank() || currentLink == null || newLink == null) {
            return unknown(rawType, id, signedAt, occurredAt, raw);
        }
        Instant effectiveAt = Json.instant(pending, "effectiveAt");
        if (effectiveAt == null) {
            effectiveAt = Json.instant(data, "currentPeriodEnd");
        }
        if (effectiveAt == null) {
            effectiveAt = occurredAt;
        }
        return SubscriptionPlanChanged.builder(
                        header(rawType, id, signedAt, occurredAt, raw),
                        id,
                        SubscriptionStatusMapping.toSpi(Json.string(data, "status")))
                .from(new Plan(currentLink, Json.string(data, "planName")))
                .to(new Plan(newLink, Json.string(pending, "planName")))
                // A pending plan is booked, not applied: the subscriber keeps what they paid for until
                // the date on it.
                .effective(EffectiveTiming.AT_PERIOD_END, effectiveAt)
                .build();
    }

    private UnknownEvent unknown(
            final String rawType, final String resourceId, final long signedAt,
            final Instant occurredAt, final String raw) {
        return new UnknownEvent(header(rawType, resourceId, signedAt, occurredAt, raw), rawType);
    }

    private static EventHeader header(
            final String rawType, final String resourceId, final long signedAt,
            final Instant occurredAt, final String raw) {
        return EventHeader.builder(AgentaOsEventId.of(rawType, resourceId, signedAt), PROVIDER_ID, occurredAt)
                .acknowledgement(ACKNOWLEDGEMENT)
                .rawPayload(raw)
                .build();
    }

    private static Plan planOf(final Map<String, Object> data) {
        final String linkId = Json.string(data, "linkId");
        return linkId == null || linkId.isBlank() ? null : new Plan(linkId, Json.string(data, "planName"));
    }

    private static Money webhookAmount(final Map<String, Object> data) {
        return Amounts.fromMinorUnitsOrNull(Json.integer(data, "amountMinor"), Json.string(data, "currency"));
    }

    // ---------------------------------------------------------------- helpers

    private static CreateCheckoutRequest.Builder recurring(final PaymentIntent intent) {
        if (intent.planRef() == null || intent.planRef().isBlank()) {
            throw new PaymentProviderException(
                    "AgentaOS creates a subscription only when a buyer pays a payment link of type"
                            + " \"subscription\"; there is no API that creates one. Create that link, then"
                            + " pass its id as the intent's planRef — a Recurrence on its own cannot be"
                            + " served.",
                    PROVIDER_ID, "recurrence_without_plan", false, null);
        }
        if (intent.recurrence() != null && BillingInterval.from(intent.recurrence()) == null) {
            throw new PaymentProviderException(
                    "AgentaOS bills monthly or yearly and nothing else, so a period of "
                            + intent.recurrence().periodLength() + " " + intent.recurrence().unit()
                            + " cannot be expressed. Rounding it would bill the subscriber on a schedule"
                            + " they did not agree to.",
                    PROVIDER_ID, "unsupported_recurrence", false, null);
        }
        return CreateCheckoutRequest.forLink(intent.planRef());
    }

    private static PaymentProviderException notFound(final String subscriptionRef) {
        return new PaymentProviderException(
                "AgentaOS listed no subscription with id \"" + subscriptionRef + "\" for this organization.",
                PROVIDER_ID, "not_found", false, null);
    }

    private static void requireRef(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
