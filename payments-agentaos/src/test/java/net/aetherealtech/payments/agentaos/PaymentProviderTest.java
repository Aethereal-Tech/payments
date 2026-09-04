package net.aetherealtech.payments.agentaos;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.Customer;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.PeriodUnit;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.Recurrence;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundRequest;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.exception.PaymentProviderException;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;

/** The SPI surface: what it declares, what it refuses, and what it does over the wire. */
class PaymentProviderTest extends GatewayTestBase {

    private static final String SESSIONS = "/api/v1/gateway/sessions";
    private static final String SUBSCRIPTIONS = "/api/v1/gateway/subscriptions";
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-04T12:00:00Z");

    private AgentaOsPaymentProvider provider() {
        return new AgentaOsPaymentProvider(client(), null, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));
    }

    @Test
    void declaresWhatItCanDoAndOmitsWhatItCannot() {
        final AgentaOsPaymentProvider provider = provider();

        assertThat(provider.id()).isEqualTo("agentaos");
        assertThat(provider.capabilities()).containsExactlyInAnyOrder(
                ProviderCapability.HOSTED_CHECKOUT,
                ProviderCapability.RECURRING_CHECKOUT,
                ProviderCapability.MACHINE_PAYMENT_URL,
                ProviderCapability.WEBHOOK_SIGNATURE,
                ProviderCapability.SUBSCRIPTIONS,
                ProviderCapability.CANCEL_AT_PERIOD_END,
                ProviderCapability.RECONCILE,
                ProviderCapability.PLAN_CHANGE);
        assertThat(provider.supports(ProviderCapability.REFUND)).isFalse();
        assertThat(provider.supports(ProviderCapability.TOKENIZATION)).isFalse();
        assertThat(provider.client()).isNotNull();
    }

    @Test
    void refundRefusesByNameRatherThanInventingAnEndpoint() {
        final UnsupportedCapabilityException e = catchThrowableOfType(UnsupportedCapabilityException.class,
                () -> provider().refund(RefundRequest.full("chk_1")));

        assertThat(e.capability()).isEqualTo(ProviderCapability.REFUND);
        assertThat(e.provider()).isEqualTo("agentaos");
        assertThat(allRequests()).isEmpty();
    }

    @Test
    void aOneOffIntentBecomesALinklessSessionCarryingItsOwnAmount() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        final RedirectTarget target = provider().startCheckout(PaymentIntent.builder("order-4471")
                .amount(Money.of("29.99", "EUR"))
                .description("A thing")
                .urls("https://shop.example/thanks", "https://shop.example/cart",
                        "https://shop.example/oops", "https://shop.example/hooks/agentaos")
                .customer(Customer.builder().email("buyer@example.com").name("A Buyer")
                        .countryCode("MK").vatNumber("MK4030000000000").build())
                .metadata(Map.of("campaign", "spring"))
                .build());

        assertThat(target.redirectUrl()).isEqualTo("https://pay.agentaos.ai/c/cs_live_9Kq2m4");
        assertThat(target.checkoutRef()).isEqualTo("cs_live_9Kq2m4");
        assertThat(target.iframe()).isFalse();
        assertThat(target.machinePayment()).contains("https://api.agentaos.ai/x402/cs_live_9Kq2m4");

        final String body = onlyRequest().getBodyAsString();
        assertThat(body)
                .contains("\"amount\":29.99")
                .contains("\"currency\":\"EUR\"")
                .contains("\"buyerEmail\":\"buyer@example.com\"")
                .contains("\"buyerVat\":\"MK4030000000000\"")
                .contains("\"successUrl\":\"https://shop.example/thanks\"")
                .contains("\"webhookUrl\":\"https://shop.example/hooks/agentaos\"")
                // AgentaOS has two return URLs, not three; errorUrl has nowhere to go.
                .doesNotContain("oops")
                .doesNotContain("linkId");
        assertThat(body).contains("\"merchantReference\":\"order-4471\"").contains("\"campaign\":\"spring\"");
        // A retried checkout must resolve to the session the first attempt created.
        assertThat(onlyRequest().getHeader("idempotency-key")).isEqualTo("order-4471");
    }

    @Test
    void aRecurringIntentOpensASessionAgainstItsSubscriptionLink() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        provider().startCheckout(PaymentIntent.builder("order-9")
                .planRef("pl_9d3")
                .recurrence(Recurrence.monthly())
                .build());

        assertThat(onlyRequest().getBodyAsString())
                .contains("\"linkId\":\"pl_9d3\"")
                .doesNotContain("\"amount\"");
    }

    @Test
    void aRecurrenceWithNoPlanRefIsRefusedWithTheReasonSpeltOut() {
        final PaymentProviderException e = catchThrowableOfType(PaymentProviderException.class,
                () -> provider().startCheckout(PaymentIntent.builder("order-9")
                        .amount(Money.of("19.99", "EUR"))
                        .recurrence(Recurrence.monthly())
                        .build()));

        assertThat(e.code()).isEqualTo("recurrence_without_plan");
        assertThat(e).hasMessageContaining("payment link of type \"subscription\"")
                .hasMessageContaining("planRef");
        assertThat(e.outcomeUnknown()).isFalse();
        assertThat(allRequests()).isEmpty();
    }

    @Test
    void aCadenceAgentaOsCannotBillIsRefusedRatherThanRounded() {
        final PaymentProviderException e = catchThrowableOfType(PaymentProviderException.class,
                () -> provider().startCheckout(PaymentIntent.builder("order-9")
                        .planRef("pl_9d3")
                        .recurrence(Recurrence.every(3, PeriodUnit.MONTH))
                        .build()));

        assertThat(e.code()).isEqualTo("unsupported_recurrence");
        assertThat(e).hasMessageContaining("monthly or yearly");
        assertThat(allRequests()).isEmpty();
    }

    @Test
    void aYearlyCadenceOnALinkIsAccepted() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        assertThat(provider().startCheckout(PaymentIntent.builder("order-9")
                .planRef("pl_9d3")
                .recurrence(Recurrence.yearly())
                .build()).checkoutRef())
                .isEqualTo("cs_live_9Kq2m4");
    }

    @Test
    void aSessionWithNowhereToSendTheBuyerIsAFailureRatherThanARedirectToNull() {
        stubJson(SESSIONS, 200, "{\"session_id\":\"cs_1\",\"checkout_url\":null}");

        final PaymentProviderException e = catchThrowableOfType(PaymentProviderException.class,
                () -> provider().startCheckout(PaymentIntent.builder("order-9")
                        .amount(Money.of("1.00", "EUR")).build()));

        assertThat(e.code()).isEqualTo("malformed_response");
        assertThat(e).hasMessageContaining("cs_1");
    }

    @Test
    void reconcilePagesTheListBecauseThereIsNoRetrieve() {
        stubGet(SUBSCRIPTIONS + "?limit=1&offset=0", Fixtures.load("subscriptions-page-1.json"));
        stubGet(SUBSCRIPTIONS + "?limit=1&offset=1", Fixtures.load("subscriptions-page-2.json"));
        final AgentaOsPaymentProvider provider = new AgentaOsPaymentProvider(
                new AgentaOsClient(config().withPageSize(1)), null, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        final SubscriptionSnapshot snapshot = provider.reconcile("sub_bbb");

        assertThat(snapshot.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(snapshot.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(snapshot.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(snapshot.plan().ref()).isEqualTo("pl_9d3");
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(snapshot.activeUntil()).contains(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(snapshot.cancelAtPeriodEnd()).isTrue();
        assertThat(snapshot.effectiveCancelDate()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(snapshot.customerRef()).isEqualTo("second@example.com");
        // Set by the adapter when the gateway answered, so two snapshots are comparable.
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(allRequests()).hasSize(2);
    }

    @Test
    void reconcileStopsAtTheFirstPageWhenItFindsItThere() {
        stubGet(SUBSCRIPTIONS + "?limit=1&offset=0", Fixtures.load("subscriptions-page-1.json"));
        final AgentaOsPaymentProvider provider = new AgentaOsPaymentProvider(
                new AgentaOsClient(config().withPageSize(1)), null, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        assertThat(provider.reconcile("sub_aaa").status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(allRequests()).hasSize(1);
    }

    @Test
    void reconcileSaysNotFoundRatherThanReturningNothing() {
        stubGet(SUBSCRIPTIONS + "?limit=100&offset=0", Fixtures.load("subscriptions-empty.json"));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> provider().reconcile("sub_zzz"));

        assertThat(e.code()).isEqualTo("not_found");
        assertThat(e).hasMessageContaining("sub_zzz");
    }

    @Test
    void reconcileGivesUpRatherThanPagingForeverOnAStuckHasMore() {
        gateway.stubFor(get(urlPathEqualTo(SUBSCRIPTIONS)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", JSON)
                .withBody(Fixtures.load("subscriptions-page-1.json"))));
        final AgentaOsPaymentProvider provider = new AgentaOsPaymentProvider(
                new AgentaOsClient(config().withPageSize(1)), null, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> provider.reconcile("sub_zzz"));

        assertThat(e.code()).isEqualTo("reconcile_exhausted");
        assertThat(allRequests()).hasSize(100);
    }

    @Test
    void cancelSubscriptionAnswersWithTheDateAccessIsOwedUntil() {
        stubJson(SUBSCRIPTIONS + "/sub_bbb/cancel", 200, Fixtures.load("subscription-cancel.json"));

        final SubscriptionSnapshot snapshot = provider().cancelSubscription("sub_bbb", true);

        assertThat(snapshot.subscriptionRef()).isEqualTo("sub_bbb");
        assertThat(snapshot.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(snapshot.cancelAtPeriodEnd()).isTrue();
        assertThat(snapshot.effectiveCancelDate()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(snapshot.plan()).isNull();
        assertThat(snapshot.amount()).isNull();
    }

    @Test
    void refusesABlankReferenceBeforeCallingAnything() {
        final AgentaOsPaymentProvider provider = provider();

        assertThatThrownBy(() -> provider.reconcile(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.cancelSubscription(null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.startCheckout(null)).isInstanceOf(NullPointerException.class);
        assertThat(allRequests()).isEmpty();
    }

    @Test
    void refusesToBeBuiltWithoutAConfigurationOrAClock() {
        assertThatThrownBy(() -> new AgentaOsPaymentProvider((AgentaOsConfig) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentaOsPaymentProvider(client(), null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentaOsPaymentProvider(null, null, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class);
    }
}
