package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.event.CheckoutCompleted;
import net.aetherealtech.payments.event.DunningExhausted;
import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.event.PaymentFailed;
import net.aetherealtech.payments.event.SubscriptionCancelled;
import net.aetherealtech.payments.event.SubscriptionCreated;
import net.aetherealtech.payments.event.SubscriptionPlanChanged;
import net.aetherealtech.payments.event.SubscriptionRenewed;
import net.aetherealtech.payments.event.UnknownEvent;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/** Every webhook AgentaOS documents, and the events they honestly become. */
class WebhookEventMappingTest {

    private static final long SIGNED_AT = WebhookVerifierTest.TIMESTAMP;

    private final AgentaOsPaymentProvider provider = new AgentaOsPaymentProvider(
            new AgentaOsClient(AgentaOsConfig.of(URI.create("http://localhost:1"), "sk_test_abc")),
            new AgentaOsWebhookVerifier(WebhookVerifierTest.SECRET, Duration.ofSeconds(300),
                    Clock.fixed(Instant.ofEpochSecond(SIGNED_AT + 1), ZoneOffset.UTC)),
            Clock.fixed(Instant.ofEpochSecond(SIGNED_AT + 1), ZoneOffset.UTC));

    @Test
    void aCompletedCheckoutCarriesTheSessionTheMerchantReferenceAndTheStringAmount() {
        final CheckoutCompleted event = (CheckoutCompleted) handle("webhook-checkout-completed.json");

        assertThat(event.checkoutRef()).isEqualTo("cs_live_9Kq2m4");
        assertThat(event.merchantReference()).isEqualTo("order-4471");
        // The webhook sends "29.99" as a string where the REST resources send a decimal number.
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("29.99"), "EUR"));
        assertThat(event.paymentRef()).isEqualTo("0xabc123");
        assertThat(event.metadata()).containsEntry("campaign", "spring");
        assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochSecond(SIGNED_AT));
        assertThat(event.acknowledgement()).isEmpty();
        assertThat(event.header().provider()).isEqualTo("agentaos");
        assertThat(event.header().rawPayload()).contains("checkout.session.completed");
    }

    @Test
    void aCreatedSubscriptionCarriesItsPlanItsMinorUnitPriceAndItsStatus() {
        final SubscriptionCreated event = (SubscriptionCreated) handle("webhook-subscription-created.json");

        assertThat(event.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(event.status()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(event.plan().ref()).isEqualTo("pl_9d3");
        assertThat(event.plan().displayName()).isEqualTo("Pro");
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(event.currentPeriodEnd()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(event.customerRef()).isEqualTo("second@example.com");
        assertThat(event.trialEndsAt()).isNull();
    }

    @Test
    void readsAWebhookPayloadWrittenInSnakeCaseToo() {
        // types.ts names the payload's fields in camelCase and the REST layer's snakeToCamel never runs
        // on a webhook body. Reading both spellings is how this survives being wrong about which.
        final SubscriptionCreated event =
                (SubscriptionCreated) handle("webhook-subscription-created-snake.json");

        assertThat(event.subscriptionRef()).isEqualTo("sub_snake");
        assertThat(event.plan().displayName()).isEqualTo("Pro");
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(event.currentPeriodEnd()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(event.customerRef()).isEqualTo("snake@example.com");
    }

    @Test
    void aRenewalCarriesTheNewExpiry() {
        final SubscriptionRenewed event = (SubscriptionRenewed) handle("webhook-subscription-renewed.json");

        assertThat(event.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(event.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(event.currentPeriodEnd()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(event.plan().ref()).isEqualTo("pl_9d3");
    }

    @Test
    void aFailedRenewalWhileRetriesContinueIsOneAttempt() {
        final PaymentFailed event = (PaymentFailed) handle("webhook-subscription-payment-failed.json");

        assertThat(event.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(event.subscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(event.subscription()).contains("sub_bbb");
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
    }

    @Test
    void aFailedRenewalThatLeftTheSubscriptionTerminalIsTheEndOfDunning() {
        // "unpaid" is Stripe's end of dunning. Sending a PaymentFailed here would leave a consumer
        // waiting for a further event that is never coming.
        final DunningExhausted event =
                (DunningExhausted) handle("webhook-subscription-payment-failed-unpaid.json");

        assertThat(event.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(event.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(event.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
    }

    @Test
    void aDeferredCancellationKeepsTheDateAccessIsOwedUntil() {
        final SubscriptionCancelled event = (SubscriptionCancelled) handle("webhook-subscription-canceled.json");

        assertThat(event.timing()).isEqualTo(EffectiveTiming.AT_PERIOD_END);
        assertThat(event.endsAtPeriodEnd()).isTrue();
        assertThat(event.effectiveAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(event.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void animmediateCancellationEndsAtTheMomentItWasSigned() {
        final SubscriptionCancelled event =
                (SubscriptionCancelled) handle("webhook-subscription-canceled-immediate.json");

        assertThat(event.timing()).isEqualTo(EffectiveTiming.IMMEDIATE);
        assertThat(event.effectiveAt()).isEqualTo(Instant.ofEpochSecond(SIGNED_AT));
        assertThat(event.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void anUpdateWithAPendingPlanIsAPlanChangeAtPeriodEnd() {
        final SubscriptionPlanChanged event =
                (SubscriptionPlanChanged) handle("webhook-subscription-updated.json");

        assertThat(event.previousPlan().ref()).isEqualTo("pl_9d3");
        assertThat(event.previousPlan().displayName()).isEqualTo("Pro");
        assertThat(event.newPlan().ref()).isEqualTo("pl_starter");
        assertThat(event.newPlan().displayName()).isEqualTo("Starter");
        assertThat(event.timing()).isEqualTo(EffectiveTiming.AT_PERIOD_END);
        assertThat(event.effectiveAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void anUpdateWithNoPendingPlanSaysSoRatherThanGuessingWhatChanged() {
        final UnknownEvent event = (UnknownEvent) handle("webhook-subscription-updated-no-plan.json");

        assertThat(event.providerEventType()).isEqualTo("subscription.updated");
        assertThat(event.header().rawPayload()).contains("sub_bbb");
    }

    @Test
    void aWalletSendIsNotAPaymentToThisMerchant() {
        // send.* is money leaving the merchant's own wallet. Mapping it to PaymentSucceeded would record
        // an inbound payment for money that went the other way.
        final UnknownEvent event = (UnknownEvent) handle("webhook-send-completed.json");

        assertThat(event.providerEventType()).isEqualTo("send.completed");
        assertThat(event.header().eventId())
                .isEqualTo(AgentaOsEventId.of("send.completed", "tx_5512", SIGNED_AT));
    }

    @Test
    void aTypeThisAdapterDoesNotModelIsKeptWithItsPayload() {
        final UnknownEvent event = (UnknownEvent) handleBody("{\"type\":\"invoice.voided\",\"data\":{}}");

        assertThat(event.providerEventType()).isEqualTo("invoice.voided");
        assertThat(event.header().rawPayload()).isEqualTo("{\"type\":\"invoice.voided\",\"data\":{}}");
    }

    @Test
    void aVerifiedBodyThatIsNotJsonIsStillAFactWorthKeeping() {
        final UnknownEvent event = (UnknownEvent) handleBody("not json at all");

        assertThat(event.providerEventType()).isNull();
        assertThat(event.header().rawPayload()).isEqualTo("not json at all");
    }

    @Test
    void aPayloadMissingTheIdentifierItsEventNeedsBecomesUnknown() {
        assertThat(handleBody("{\"type\":\"checkout.session.completed\",\"data\":{}}"))
                .isInstanceOf(UnknownEvent.class);
        assertThat(handleBody("{\"type\":\"subscription.created\",\"data\":{}}"))
                .isInstanceOf(UnknownEvent.class);
        assertThat(handleBody("{\"type\":\"subscription.payment_failed\",\"data\":{}}"))
                .isInstanceOf(UnknownEvent.class);
        assertThat(handleBody("{\"type\":\"subscription.canceled\",\"data\":{}}"))
                .isInstanceOf(UnknownEvent.class);
        assertThat(handleBody("{\"type\":\"subscription.updated\",\"data\":{\"id\":\"s\"}}"))
                .isInstanceOf(UnknownEvent.class);
    }

    @Test
    void aRenewalWithNoNewExpiryIsNotTurnedIntoAGuess() {
        // The new expiry IS the event; substituting a computed date would write a wrong one into a
        // licence gate.
        assertThat(handleBody("{\"type\":\"subscription.renewed\",\"data\":{\"id\":\"sub_bbb\"}}"))
                .isInstanceOf(UnknownEvent.class);
    }

    @Test
    void aPriceInACurrencyWithNoStatedExponentIsOmittedRatherThanScaledWrongly() {
        final SubscriptionCreated event = (SubscriptionCreated) handleBody(
                "{\"type\":\"subscription.created\",\"data\":{\"id\":\"sub_jpy\",\"status\":\"active\","
                        + "\"currency\":\"JPY\",\"amountMinor\":1999,\"linkId\":\"pl_x\"}}");

        assertThat(event.amount()).isNull();
        assertThat(event.subscriptionRef()).isEqualTo("sub_jpy");
    }

    @Test
    void anUpdateWhoseNewPlanNamesNoLinkStaysUnknown() {
        assertThat(handleBody("{\"type\":\"subscription.updated\",\"data\":{\"id\":\"s\",\"linkId\":\"a\","
                + "\"pendingPlan\":{\"planName\":\"Starter\"}}}"))
                .isInstanceOf(UnknownEvent.class);
    }

    @Test
    void anUpdateWithNoEffectiveDateFallsBackToThePeriodEndThenToTheSignature() {
        final SubscriptionPlanChanged onPeriodEnd = (SubscriptionPlanChanged) handleBody(
                "{\"type\":\"subscription.updated\",\"data\":{\"id\":\"s\",\"linkId\":\"a\","
                        + "\"currentPeriodEnd\":\"2026-12-01T00:00:00Z\","
                        + "\"pendingPlan\":{\"linkId\":\"b\"}}}");
        final SubscriptionPlanChanged onSignature = (SubscriptionPlanChanged) handleBody(
                "{\"type\":\"subscription.updated\",\"data\":{\"id\":\"s\",\"linkId\":\"a\","
                        + "\"pendingPlan\":{\"linkId\":\"b\"}}}");

        assertThat(onPeriodEnd.effectiveAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(onSignature.effectiveAt()).isEqualTo(Instant.ofEpochSecond(SIGNED_AT));
    }

    @Test
    void aDeferredCancellationWithNoDatesAtAllFallsBackToTheSignature() {
        final SubscriptionCancelled event = (SubscriptionCancelled) handleBody(
                "{\"type\":\"subscription.canceled\",\"data\":{\"id\":\"s\",\"cancelAtPeriodEnd\":true}}");

        assertThat(event.timing()).isEqualTo(EffectiveTiming.AT_PERIOD_END);
        assertThat(event.effectiveAt()).isEqualTo(Instant.ofEpochSecond(SIGNED_AT));
    }

    @Test
    void aCancellationPrefersTheEffectiveCancelDateWhenTheGatewaySendsOne() {
        final SubscriptionCancelled event = (SubscriptionCancelled) handleBody(
                "{\"type\":\"subscription.canceled\",\"data\":{\"id\":\"s\",\"cancelAtPeriodEnd\":true,"
                        + "\"effectiveCancelDate\":\"2027-01-01T00:00:00Z\","
                        + "\"currentPeriodEnd\":\"2026-10-01T00:00:00Z\"}}");

        assertThat(event.effectiveAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void theSameDeliveryTwiceIsTheSameEventId() {
        final PaymentEvent first = handle("webhook-subscription-created.json");
        final PaymentEvent second = handle("webhook-subscription-created.json");

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.eventId())
                .isEqualTo(AgentaOsEventId.of("subscription.created", "sub_bbb", SIGNED_AT));
        assertThat(first.eventId()).isEqualTo("agentaos_e343feaaa09aec635c43036a1e41a467");
    }

    @Test
    void twoDifferentEventsAboutTheSameSubscriptionAreDifferentIds() {
        assertThat(handle("webhook-subscription-created.json").eventId())
                .isNotEqualTo(handle("webhook-subscription-renewed.json").eventId());
    }

    @Test
    void refusesEverythingBeforeItReadsAnything() {
        final InboundWebhook unsigned = WebhookVerifierTest.webhook(null, "{\"type\":\"ping\"}");

        final WebhookVerificationException e = catchThrowableOfType(
                WebhookVerificationException.class, () -> provider.handleWebhook(unsigned));

        assertThat(e.reason()).isEqualTo("signature_header_absent");
        assertThat(e.provider()).isEqualTo("agentaos");
    }

    @Test
    void refusesEveryWebhookWhenNoSecretIsConfigured() {
        final AgentaOsPaymentProvider unverifying =
                new AgentaOsPaymentProvider(AgentaOsConfig.of(URI.create("http://localhost:1"), "sk_test_x"));

        final WebhookVerificationException e = catchThrowableOfType(WebhookVerificationException.class,
                () -> unverifying.handleWebhook(WebhookVerifierTest.webhook("t=1,v1=aa", "{}")));

        assertThat(e.reason()).isEqualTo("no_webhook_secret");
        assertThatThrownBy(() -> unverifying.handleWebhook(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------- helpers

    private PaymentEvent handle(final String fixture) {
        return handleBody(Fixtures.load(fixture));
    }

    private PaymentEvent handleBody(final String body) {
        final String signature = WebhookVerifierTest.header(
                SIGNED_AT, WebhookVerifierTest.hmacHex(WebhookVerifierTest.SECRET, SIGNED_AT + "." + body));
        return provider.handleWebhook(WebhookVerifierTest.webhook(signature, body));
    }
}
