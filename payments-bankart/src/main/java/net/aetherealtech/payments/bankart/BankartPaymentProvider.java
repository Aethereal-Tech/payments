package net.aetherealtech.payments.bankart;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.PaymentProvider;
import net.aetherealtech.payments.PeriodUnit;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.Recurrence;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundReceipt;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.bankart.exception.BankartApiException;
import net.aetherealtech.payments.bankart.exception.BankartException;
import net.aetherealtech.payments.bankart.exception.BankartSignatureException;
import net.aetherealtech.payments.bankart.exception.BankartTransactionException;
import net.aetherealtech.payments.bankart.exception.BankartTransportException;
import net.aetherealtech.payments.bankart.model.PaymentRequest;
import net.aetherealtech.payments.bankart.model.RedirectResult;
import net.aetherealtech.payments.bankart.model.Schedule;
import net.aetherealtech.payments.bankart.model.SchedulePeriodUnit;
import net.aetherealtech.payments.bankart.model.ScheduleData;
import net.aetherealtech.payments.bankart.model.ScheduleResponse;
import net.aetherealtech.payments.bankart.model.ScheduleStatus;
import net.aetherealtech.payments.bankart.model.TransactionIndicator;
import net.aetherealtech.payments.bankart.model.TransactionResponse;
import net.aetherealtech.payments.bankart.model.TransactionType;
import net.aetherealtech.payments.bankart.notification.Notification;
import net.aetherealtech.payments.bankart.notification.NotificationVerifier;
import net.aetherealtech.payments.bankart.signing.SignedRequest;
import net.aetherealtech.payments.event.ChargebackOpened;
import net.aetherealtech.payments.event.ChargebackReversed;
import net.aetherealtech.payments.event.CheckoutCompleted;
import net.aetherealtech.payments.event.EventHeader;
import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.event.PaymentFailed;
import net.aetherealtech.payments.event.PaymentSucceeded;
import net.aetherealtech.payments.event.Refunded;
import net.aetherealtech.payments.event.SubscriptionCancelled;
import net.aetherealtech.payments.event.SubscriptionCreated;
import net.aetherealtech.payments.event.SubscriptionRenewed;
import net.aetherealtech.payments.event.UnknownEvent;
import net.aetherealtech.payments.exception.PaymentDeclinedException;
import net.aetherealtech.payments.exception.PaymentProviderException;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/**
 * Bankart as a {@link PaymentProvider}.
 *
 * <p>An adapter over {@link BankartClient} rather than a replacement for it. Preauthorize, capture,
 * void, payout, incremental authorization, options and the four schedule operations this interface
 * has no vocabulary for stay on the client, and a caller who has chosen Bankart deliberately reaches
 * for them there.
 *
 * <h2>What Bankart cannot say</h2>
 *
 * <p><strong>There is no schedule notification.</strong> {@code TransactionType} has no
 * {@code SCHEDULE} value, and the gateway defines exactly one callback shape for everything. A
 * recurring charge therefore arrives as an ordinary {@code DEBIT} callback whose {@code scheduleData}
 * happens to be populated, and there is no discriminator anywhere in it for "the schedule was
 * paused" against "the schedule was cancelled" against "a renewal failed" — only the schedule's
 * status as it stands now, which is the same field whatever caused the callback.
 *
 * <p>What follows from that, precisely:
 *
 * <ul>
 *   <li>{@link net.aetherealtech.payments.event.SubscriptionExpired},
 *       {@link net.aetherealtech.payments.event.DunningExhausted},
 *       {@link net.aetherealtech.payments.event.TrialStarted},
 *       {@link net.aetherealtech.payments.event.TrialEnding} and
 *       {@link net.aetherealtech.payments.event.SubscriptionPlanChanged} are NEVER produced from a
 *       webhook. Bankart has no trial concept, no dunning counter, no expiry distinct from
 *       cancellation, and no callback that reports an {@code updateSchedule}.</li>
 *   <li>A schedule that has been PAUSED has no event of its own in this SPI, so it arrives as an
 *       {@link UnknownEvent} rather than as a cancellation — pausing is reversible and
 *       {@link SubscriptionCancelled} is not.</li>
 *   <li>{@link CheckoutCompleted} is produced only for a completed hosted REGISTER. A debit carries
 *       no flag saying whether it was paid on the hosted page or server-to-server, so a hosted
 *       checkout's own completion arrives as {@link PaymentSucceeded}; match it on
 *       {@code merchantReference}.</li>
 *   <li>The first charge of a debit-with-register schedule is indistinguishable from a renewal, and
 *       so is reported as {@link SubscriptionRenewed}. Only a REGISTER callback carrying
 *       {@code scheduleData} can be told apart, and that one is {@link SubscriptionCreated}.</li>
 *   <li>A PREAUTHORIZE or INCREMENTAL-AUTHORIZATION callback has reserved funds and settled nothing.
 *       This SPI has no "authorized, not captured" event, so it arrives as an {@link UnknownEvent}
 *       rather than as a {@link PaymentSucceeded} a consumer would grant access on.</li>
 * </ul>
 *
 * <p><strong>{@link #reconcile(String)} is the answer to all of it.</strong> {@code GET
 * /schedule/{apiKey}/{scheduleId}/get} states the schedule's status outright, and polling it is how
 * a licence gate stays correct without a callback that says what happened.
 *
 * <h2>Status mapping</h2>
 *
 * <table>
 *   <caption>{@code ScheduleStatus} to {@link SubscriptionStatus}</caption>
 *   <tr><td>{@code ACTIVE}</td><td>{@link SubscriptionStatus#ACTIVE}</td></tr>
 *   <tr><td>{@code PAUSED}</td><td>{@link SubscriptionStatus#PAUSED}</td></tr>
 *   <tr><td>{@code CANCELLED}</td><td>{@link SubscriptionStatus#CANCELLED}</td></tr>
 *   <tr><td>{@code ERROR}</td><td>{@link SubscriptionStatus#PAST_DUE}</td></tr>
 *   <tr><td>{@code CREATE-PENDING}</td><td>{@link SubscriptionStatus#INCOMPLETE}</td></tr>
 *   <tr><td>{@code NON-EXISTING}, anything unrecognised</td><td>{@link SubscriptionStatus#UNKNOWN}</td></tr>
 * </table>
 *
 * <p>{@code ERROR} is the one that took a decision. It is not {@code UNKNOWN}: that value is
 * reserved for a status this SPI does not model, and telling a consumer "act on nothing" about a
 * status Bankart named would hide a real and actionable state. It is not {@code CANCELLED} or
 * {@code EXPIRED} either, which would revoke access over something the gateway has not called
 * terminal — the schedule still exists and can be updated. {@code PAST_DUE} is the only value that
 * says billing is failing while the subscription is alive, and leaves the access decision where it
 * belongs.
 *
 * <p>Thread-safe; build one and share it.
 */
public final class BankartPaymentProvider implements PaymentProvider {

    public static final String ID = "bankart";

    /**
     * {@link ProviderCapability#CANCEL_AT_PERIOD_END} is absent because nothing in the API can
     * express it. {@code cancelSchedule} takes an empty body — no flag, no date, no field anywhere
     * asking for the end of the paid period — and pausing is not the same thing, since a paused
     * schedule resumes rather than ending. Declaring it would mean {@code cancelSubscription(ref,
     * true)} cancelling immediately and reporting success, which takes away access somebody has paid
     * for.
     *
     * <p>{@link ProviderCapability#PLAN_CHANGE} IS declared: {@code updateSchedule} changes a live
     * schedule's amount, currency and cadence without cancelling and recreating it, which is exactly
     * what the capability names. Two caveats a consumer should read first — Bankart has no plan
     * catalogue, so the "plan" being changed is the schedule's own price and cadence rather than a
     * named product; and this SPI has no plan-change method, so the operation is reached through
     * {@link BankartClient#updateSchedule}, and no
     * {@link net.aetherealtech.payments.event.SubscriptionPlanChanged} can ever arrive by webhook to
     * confirm it.
     */
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.HOSTED_CHECKOUT,
            ProviderCapability.RECURRING_CHECKOUT,
            ProviderCapability.WEBHOOK_SIGNATURE,
            ProviderCapability.REFUND,
            ProviderCapability.SUBSCRIPTIONS,
            ProviderCapability.PLAN_CHANGE,
            ProviderCapability.RECONCILE,
            ProviderCapability.TOKENIZATION);

    /** The API's {@code DateTimeZone}: {@code 2019-09-30T01:00:00+00:00}, offset always spelled out. */
    private static final DateTimeFormatter DATE_TIME_ZONE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    /** Both spellings the gateway's own examples use for the signed {@code Date}. */
    private static final DateTimeFormatter[] DATE_HEADER_FORMATS = {
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC)
    };

    private final BankartClient client;
    private final NotificationVerifier verifier;
    private final Clock clock;

    public BankartPaymentProvider(BankartClient client, NotificationVerifier verifier) {
        this(client, verifier, Clock.systemUTC());
    }

    public BankartPaymentProvider(BankartClient client, NotificationVerifier verifier, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return CAPABILITIES;
    }

    // ---------------------------------------------------------------- checkout

    @Override
    public RedirectTarget startCheckout(PaymentIntent intent) {
        Objects.requireNonNull(intent, "intent");
        PaymentRequest request = intent.isRecurring() ? recurring(intent) : oneOff(intent).build();
        RedirectResult redirect = translating(() -> client.startCheckout(request));
        return new RedirectTarget(redirect.redirectUrl(), redirect.isIframe(), null, redirect.uuid());
    }

    // ---------------------------------------------------------------- webhooks

    @Override
    public PaymentEvent handleWebhook(InboundWebhook webhook) {
        Objects.requireNonNull(webhook, "webhook");
        String signature = required(webhook, NotificationVerifier.SIGNATURE_HEADER, "missing-signature-header");
        // X-Date first: the docs give it precedence over Date precisely because a proxy may rewrite
        // Date, and the signature covers whichever string the gateway put in the header it signed.
        String date = webhook.header("X-Date")
                .or(() -> webhook.header("Date"))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> refuse("Notification carried no Date or X-Date header", "missing-date-header"));
        String contentType = webhook.header("Content-Type").orElse("");

        SignedRequest signed = new SignedRequest(
                webhook.method(), webhook.body(), contentType, date, webhook.requestUri());

        Notification notification;
        try {
            notification = verifier.verifyAndParse(signed, signature);
        } catch (BankartSignatureException e) {
            throw refuse(e.getMessage(), reasonFor(e));
        }
        return toEvent(notification, header(notification, date, webhook.bodyAsString()));
    }

    // ---------------------------------------------------------------- subscriptions

    @Override
    public SubscriptionSnapshot reconcile(String subscriptionRef) {
        requireText(subscriptionRef, "subscriptionRef");
        ScheduleResponse response = translating(() -> client.showSchedule(subscriptionRef));
        raiseIfRefused(response, "Could not read schedule " + subscriptionRef);
        return snapshot(subscriptionRef, response, null);
    }

    @Override
    public SubscriptionSnapshot cancelSubscription(String subscriptionRef, boolean atPeriodEnd) {
        requireText(subscriptionRef, "subscriptionRef");
        if (atPeriodEnd) {
            throw new UnsupportedCapabilityException(ID, ProviderCapability.CANCEL_AT_PERIOD_END);
        }
        ScheduleResponse response = translating(() -> client.cancelSchedule(subscriptionRef));
        raiseIfRefused(response, "Could not cancel schedule " + subscriptionRef);
        // A Bankart cancellation is in force the moment the gateway accepts it; there is no scheduled
        // form, so the effective date is now rather than anything the response carries.
        return snapshot(subscriptionRef, response, clock.instant());
    }

    // ---------------------------------------------------------------- refunds

    @Override
    public RefundReceipt refund(net.aetherealtech.payments.RefundRequest request) {
        Objects.requireNonNull(request, "request");
        // Bankart's refund declares merchantTransactionId, amount and currency all required, so
        // neither a full refund nor an unreferenced one can be expressed. Refusing by name beats
        // inventing a total the gateway would then disagree with after a reconciliation restatement.
        if (request.isFull()) {
            throw new PaymentProviderException(
                    "Bankart cannot refund \"everything\": its refund requires an explicit amount and currency. "
                            + "Read the charged amount back with statusByUuid and refund that.",
                    ID, null, null);
        }
        if (request.merchantReference() == null || request.merchantReference().isBlank()) {
            throw new PaymentProviderException(
                    "Bankart requires a merchantTransactionId on a refund; it is also the idempotency handle "
                            + "that stops a retry paying the buyer back twice.",
                    ID, null, null);
        }

        Money amount = request.amount();
        // The isError check is INSIDE translating: client.refund answers a failed refund rather than
        // throwing, and raising a BankartTransactionException outside this would let a gateway-shaped
        // exception past the SPI boundary.
        TransactionResponse response = translating(() -> {
            TransactionResponse refund = client.refund(
                    net.aetherealtech.payments.bankart.model.RefundRequest.of(
                            request.merchantReference(), request.paymentRef(), amount.amount(), amount.currency()));
            if (refund.isError()) {
                throw new BankartTransactionException(refund);
            }
            return refund;
        });
        return new RefundReceipt(
                response.uuid(),
                request.paymentRef(),
                amount,
                response.returnType() == net.aetherealtech.payments.bankart.model.ReturnType.PENDING,
                clock.instant());
    }

    // ---------------------------------------------------------------- checkout plumbing

    private PaymentRequest.Builder oneOff(PaymentIntent intent) {
        Money amount = intent.amount();
        if (amount == null) {
            throw new PaymentProviderException(
                    "Bankart has no plan catalogue, so a checkout must name its own amount; "
                            + "this intent carried only planRef \"" + intent.planRef() + "\".",
                    ID, null, null);
        }
        return PaymentRequest.builder(intent.merchantReference(), amount.amount(), amount.currency())
                .redirectUrls(intent.successUrl(), intent.cancelUrl(), intent.errorUrl(), intent.callbackUrl())
                .description(intent.description())
                .customer(customer(intent.customer()))
                .extraData(intent.metadata().isEmpty() ? null : intent.metadata());
    }

    /**
     * The SPI's five customer fields in Bankart's twenty-nine.
     *
     * <p>Two of them do not fit and are handled rather than dropped silently:
     *
     * <ul>
     *   <li>The SPI holds one {@code name} because providers disagree on splitting it; Bankart wants
     *       {@code firstName} and {@code lastName} separately, and feeds both to the 3-D Secure
     *       frictionless check. It is split on the LAST space, which is a guess: it is right for
     *       "Ана Петровска" and wrong for a compound surname. A caller who needs the split to be
     *       right builds a {@code PaymentRequest} with Bankart's own {@code Customer} directly.</li>
     *   <li>{@code vatNumber} has no field anywhere in Bankart's customer object — not
     *       {@code nationalId}, which is a personal identifier rather than a business one — so it is
     *       dropped. Sending it as something it is not would be worse than losing it.</li>
     * </ul>
     */
    private static net.aetherealtech.payments.bankart.model.Customer customer(
            net.aetherealtech.payments.Customer customer) {
        if (customer == null) {
            return null;
        }
        String name = customer.name();
        int split = name == null ? -1 : name.trim().lastIndexOf(' ');
        return net.aetherealtech.payments.bankart.model.Customer.builder()
                .identification(customer.reference())
                .email(customer.email())
                .firstName(split < 0 ? name : name.trim().substring(0, split))
                .lastName(split < 0 ? null : name.trim().substring(split + 1))
                .billingCountry(customer.countryCode())
                .build();
    }

    private PaymentRequest recurring(PaymentIntent intent) {
        Recurrence recurrence = intent.recurrence();
        if (recurrence == null) {
            throw new PaymentProviderException(
                    "Bankart has no plan catalogue: a subscription is a schedule attached to a registered "
                            + "instrument, so a recurring checkout needs a Recurrence (periodLength + unit). "
                            + "planRef \"" + intent.planRef() + "\" names nothing this gateway knows.",
                    ID, null, null);
        }
        if (recurrence.trialDays() != null) {
            throw new PaymentProviderException(
                    "Bankart's schedule has no trial: it can defer the first charge with startDateTime, but "
                            + "nothing distinguishes a deferred charge from a free trial, and a consumer gating "
                            + "access on TRIALING would be acting on a state the gateway never reports.",
                    ID, null, null);
        }
        Money amount = intent.amount();
        if (amount == null) {
            throw new PaymentProviderException(
                    "A Bankart schedule requires its own amount and currency; this recurring intent carried "
                            + "neither, only planRef \"" + intent.planRef() + "\".",
                    ID, null, null);
        }

        Schedule schedule = new Schedule(
                amount.amount(),
                amount.currency(),
                recurrence.periodLength(),
                periodUnit(recurrence.unit()),
                recurrence.startAt() == null ? null : DATE_TIME_ZONE.format(recurrence.startAt()),
                null,
                intent.callbackUrl());

        return oneOff(intent)
                // withRegister is what stores the instrument; without it the schedule has nothing to
                // charge on the second cycle, and the gateway would accept the request regardless.
                .withRegister(true)
                // The scheme rules make this load-bearing rather than informational: the first charge
                // of a series must be declared INITIAL or the series that follows attracts declines.
                .transactionIndicator(TransactionIndicator.INITIAL)
                .schedule(schedule)
                .build();
    }

    private static SchedulePeriodUnit periodUnit(PeriodUnit unit) {
        return switch (unit) {
            case DAY -> SchedulePeriodUnit.DAY;
            case WEEK -> SchedulePeriodUnit.WEEK;
            case MONTH -> SchedulePeriodUnit.MONTH;
            case YEAR -> SchedulePeriodUnit.YEAR;
        };
    }

    // ---------------------------------------------------------------- event mapping

    private EventHeader header(Notification notification, String date, String rawPayload) {
        return EventHeader.builder(eventId(notification), ID, occurredAt(date))
                .acknowledgement(Notification.ACKNOWLEDGEMENT)
                .rawPayload(rawPayload)
                .build();
    }

    /**
     * The gateway's {@code uuid}, which is stable across the retry schedule and is what makes a
     * redelivery deduplicable. A callback without one — which the documented shape does not produce,
     * but a body is only as good as what arrived — falls back to
     * {@code <transactionType>:<merchantTransactionId>}, still stable across redeliveries of the same
     * event because both halves are fixed at the transaction, not at the delivery.
     */
    private static String eventId(Notification notification) {
        if (notification.uuid() != null && !notification.uuid().isBlank()) {
            return notification.uuid();
        }
        String type = notification.transactionType() == null
                ? TransactionType.UNKNOWN.name() : notification.transactionType().name();
        return type + ":" + notification.merchantTransactionId();
    }

    /**
     * The signed {@code Date}, which is when the gateway sent this delivery. Bankart puts no
     * timestamp in the notification body at all, so there is nothing closer to when the event
     * happened; a redelivery therefore carries a later {@code occurredAt} than the original, and
     * {@code eventId} rather than ordering is what dedupes it.
     */
    private Instant occurredAt(String date) {
        for (DateTimeFormatter format : DATE_HEADER_FORMATS) {
            try {
                return ZonedDateTime.parse(date, format).toInstant();
            } catch (DateTimeParseException ignored) {
                // try the next accepted spelling
            }
        }
        return clock.instant();
    }

    private PaymentEvent toEvent(Notification notification, EventHeader header) {
        Money amount = money(notification.amount(), notification.currency());
        TransactionType type = notification.transactionType();

        // Every typed event in this SPI is built around a provider reference, and Bankart's is the
        // uuid. The documented callback shape always carries one; a body that does not is still a
        // verified fact, but there is nothing to hang a payment or a refund on.
        if (notification.uuid() == null || notification.uuid().isBlank()) {
            return UnknownEvent.of(header, describe(notification));
        }
        if (type == TransactionType.CHARGEBACK) {
            return chargeback(notification, header, amount);
        }
        if (type == TransactionType.CHARGEBACK_REVERSAL) {
            return chargebackReversal(notification, header, amount);
        }
        if (type == TransactionType.REFUND) {
            return Refunded.builder(header, notification.uuid())
                    .paymentRef(notification.merchantTransactionId())
                    .amount(amount)
                    .merchantReference(notification.merchantTransactionId())
                    .reason(notification.message())
                    .build();
        }
        if (notification.isScheduled()) {
            return subscriptionEvent(notification, header, amount);
        }
        if (notification.isError()) {
            return failure(notification, header, amount, null, null);
        }
        if (notification.isSuccess()) {
            return success(notification, header, amount, type);
        }
        // PENDING, and anything whose result did not parse: a verified fact we cannot interpret is
        // still a fact, and the SPI has nowhere else to put one.
        return UnknownEvent.of(header, describe(notification));
    }

    private static PaymentEvent success(
            Notification notification, EventHeader header, Money amount, TransactionType type) {
        if (type == TransactionType.REGISTER) {
            return CheckoutCompleted.builder(header, notification.uuid())
                    .merchantReference(notification.merchantTransactionId())
                    .amount(amount)
                    .paymentRef(notification.uuid())
                    .build();
        }
        // PaymentSucceeded means money moved, so only the two transaction types that move it qualify.
        // A preauthorize or an incremental authorization has reserved funds and settled nothing, and
        // this SPI has no event for "authorized, not captured" — a consumer that granted access on one
        // would be giving away the product before the capture. An unreadable type gets the same
        // treatment: never claim a charge landed without knowing what kind of transaction it was.
        if (type == TransactionType.DEBIT || type == TransactionType.CAPTURE) {
            return PaymentSucceeded.builder(header, notification.uuid())
                    .merchantReference(notification.merchantTransactionId())
                    .amount(amount)
                    .build();
        }
        return UnknownEvent.of(header, describe(notification));
    }

    private PaymentEvent subscriptionEvent(Notification notification, EventHeader header, Money amount) {
        ScheduleData schedule = notification.scheduleData();
        String subscriptionRef = schedule.scheduleId();
        SubscriptionStatus status = subscriptionStatus(schedule.scheduleStatus());

        if (schedule.scheduleStatus() == ScheduleStatus.CANCELLED) {
            return SubscriptionCancelled.builder(
                            header, subscriptionRef, status, EffectiveTiming.IMMEDIATE, header.occurredAt())
                    .reason(notification.message())
                    .build();
        }
        if (schedule.scheduleStatus() == ScheduleStatus.PAUSED) {
            // A pause is reversible and this SPI has no event for it. SubscriptionCancelled would be
            // wrong in the direction that matters: a consumer would revoke access permanently for a
            // subscription the merchant intends to resume.
            return UnknownEvent.of(header, "schedule-paused");
        }
        if (notification.isError()) {
            return failure(notification, header, amount, subscriptionRef, status);
        }
        if (!notification.isSuccess()) {
            return UnknownEvent.of(header, describe(notification));
        }
        if (notification.transactionType() == TransactionType.REGISTER) {
            return SubscriptionCreated.builder(header, subscriptionRef, status)
                    .amount(amount)
                    .currentPeriodEnd(parseDateTimeZone(schedule.scheduledAt()))
                    .merchantReference(notification.merchantTransactionId())
                    .build();
        }
        Instant nextCharge = parseDateTimeZone(schedule.scheduledAt());
        if (nextCharge == null) {
            // SubscriptionRenewed exists to tell a licence gate how long access is owed for, and
            // without scheduledAt there is no answer. The money still moved, so it is reported as the
            // payment it was, carrying the subscription it belongs to.
            return PaymentSucceeded.builder(header, notification.uuid())
                    .merchantReference(notification.merchantTransactionId())
                    .amount(amount)
                    .subscriptionRef(subscriptionRef)
                    .build();
        }
        return SubscriptionRenewed.builder(header, subscriptionRef, status, nextCharge)
                .amount(amount)
                .paymentRef(notification.uuid())
                .build();
    }

    private static PaymentEvent failure(
            Notification notification,
            EventHeader header,
            Money amount,
            String subscriptionRef,
            SubscriptionStatus status) {
        return PaymentFailed.builder(header)
                .paymentRef(notification.uuid())
                .merchantReference(notification.merchantTransactionId())
                .amount(amount)
                .subscriptionRef(subscriptionRef)
                .subscriptionStatus(status)
                // The gateway's consolidated code, never the adapter's: the docs reserve the right to
                // reword messages, and adapterCode has thousands of values across acquirers.
                .declineCode(notification.code() == null ? null : String.valueOf(notification.code()))
                .reason(notification.message())
                .build();
    }

    private static PaymentEvent chargeback(Notification notification, EventHeader header, Money amount) {
        var data = notification.chargebackData();
        String paymentRef = data == null ? notification.uuid() : data.originalUuid();
        return ChargebackOpened.builder(header, paymentRef)
                .chargebackRef(notification.uuid())
                .amount(data == null ? amount : money(data.amount(), data.currency()))
                .reason(data == null ? notification.message() : data.reason())
                .merchantReference(data == null
                        ? notification.merchantTransactionId() : data.originalMerchantTransactionId())
                .build();
    }

    private static PaymentEvent chargebackReversal(Notification notification, EventHeader header, Money amount) {
        var data = notification.chargebackReversalData();
        String paymentRef = data == null ? notification.uuid() : data.originalUuid();
        return ChargebackReversed.builder(header, paymentRef)
                .chargebackRef(data == null ? notification.uuid() : data.chargebackUuid())
                .amount(data == null ? amount : money(data.amount(), data.currency()))
                .reason(data == null ? notification.message() : data.reason())
                .merchantReference(data == null
                        ? notification.merchantTransactionId() : data.originalMerchantTransactionId())
                .build();
    }

    private static String describe(Notification notification) {
        return (notification.result() == null ? "UNKNOWN" : notification.result().name())
                + ":" + (notification.transactionType() == null
                        ? TransactionType.UNKNOWN.name() : notification.transactionType().name());
    }

    // ---------------------------------------------------------------- subscription plumbing

    static SubscriptionStatus subscriptionStatus(ScheduleStatus status) {
        if (status == null) {
            return SubscriptionStatus.UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> SubscriptionStatus.ACTIVE;
            case PAUSED -> SubscriptionStatus.PAUSED;
            case CANCELLED -> SubscriptionStatus.CANCELLED;
            case ERROR -> SubscriptionStatus.PAST_DUE;
            case CREATE_PENDING -> SubscriptionStatus.INCOMPLETE;
            case NON_EXISTING, UNKNOWN -> SubscriptionStatus.UNKNOWN;
        };
    }

    private SubscriptionSnapshot snapshot(String subscriptionRef, ScheduleResponse response, Instant cancelledAt) {
        return SubscriptionSnapshot.builder(
                        subscriptionRef, subscriptionStatus(response.newStatus()), clock.instant())
                // The paid period runs until the next charge is due, which is what scheduledAt names.
                // Pause and cancel answer without one, and the field is then legitimately absent.
                .currentPeriodEnd(parseDateTimeZone(response.scheduledAt()))
                .customerRef(response.registration().orElse(null))
                .effectiveCancelDate(cancelledAt)
                .build();
    }

    private void raiseIfRefused(ScheduleResponse response, String message) {
        if (response.isError()) {
            throw new PaymentProviderException(
                    message + ": " + (response.errorMessage() == null ? "no reason given" : response.errorMessage()),
                    ID,
                    response.errorCode() == null ? null : String.valueOf(response.errorCode()),
                    null);
        }
    }

    // ---------------------------------------------------------------- translation

    /**
     * Everything Bankart-shaped stops here.
     *
     * <p>{@link BankartTransportException} becomes an {@code outcomeUnknown} provider exception and
     * nothing else does: it is the one case where the payment may already have gone through, and a
     * caller that retried it would charge the buyer twice. Every other failure is a verdict the
     * gateway actually stated.
     */
    private <T> T translating(Supplier<T> call) {
        try {
            return call.get();
        } catch (BankartTransportException e) {
            throw new PaymentProviderException(e.getMessage(), ID, null, true, e);
        } catch (BankartTransactionException e) {
            throw new PaymentDeclinedException(
                    e.getMessage(),
                    ID,
                    e.firstError().map(error -> String.valueOf(error.errorCode())).orElse(null));
        } catch (BankartApiException e) {
            throw new PaymentProviderException(e.getMessage(), ID, String.valueOf(e.errorCode()), false, e);
        } catch (BankartException e) {
            throw new PaymentProviderException(e.getMessage(), ID, null, false, e);
        }
    }

    private static String required(InboundWebhook webhook, String header, String reason) {
        return webhook.header(header)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> refuse("Notification carried no " + header + " header", reason));
    }

    private static WebhookVerificationException refuse(String message, String reason) {
        return new WebhookVerificationException(message, ID, reason);
    }

    /**
     * {@link BankartSignatureException} carries a message and nothing structured, while
     * {@link WebhookVerificationException#reason()} exists to name the component an operator can act
     * on. Classifying on the message is the only bridge, and the three messages it produces are all
     * from {@code NotificationVerifier} in this same module.
     */
    private static String reasonFor(BankartSignatureException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("did not match")) {
            return "signature-mismatch";
        }
        if (message.contains("unparseable")) {
            return "date-unparseable";
        }
        if (message.contains("window")) {
            return "date-outside-window";
        }
        return "signature-rejected";
    }

    private static Money money(BigDecimal amount, String currency) {
        return amount == null || currency == null ? null : new Money(amount, currency);
    }

    /** Null for an absent or unrecognisable timestamp: a wrong period end is worse than none. */
    private static Instant parseDateTimeZone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DATE_TIME_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
