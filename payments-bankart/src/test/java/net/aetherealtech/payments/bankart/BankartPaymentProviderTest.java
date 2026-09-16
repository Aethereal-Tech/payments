package net.aetherealtech.payments.bankart;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.payments.Customer;
import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.PeriodUnit;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.Recurrence;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundReceipt;
import net.aetherealtech.payments.RefundRequest;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.bankart.model.ScheduleStatus;
import net.aetherealtech.payments.bankart.notification.NotificationVerifier;
import net.aetherealtech.payments.bankart.signing.BodyDigest;
import net.aetherealtech.payments.bankart.signing.HmacSigner;
import net.aetherealtech.payments.bankart.signing.SignedRequest;
import net.aetherealtech.payments.event.ChargebackOpened;
import net.aetherealtech.payments.event.ChargebackReversed;
import net.aetherealtech.payments.event.CheckoutCompleted;
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

/** The SPI adapter: what it sends, what it refuses, and what it turns a verified callback into. */
class BankartPaymentProviderTest extends GatewayTestBase {

    private static final String CALLBACK_URI = "/payments/bankart/callback";
    private static final String SCHEDULE_ID = "SC-1234-1234-1234-1234-1234-1234";
    private static final Instant NOW = Instant.parse("2020-07-21T13:15:03Z");
    private static final String DATE = HmacSigner.formatDate(NOW);

    private static final String REDIRECT = """
            {
              "success": true,
              "uuid": "abcde12345abcde12345",
              "purchaseId": "20190927-abcde12345abcde12345",
              "returnType": "REDIRECT",
              "redirectType": "fullpage",
              "redirectUrl": "https://gateway.example/pay/abcde12345",
              "paymentMethod": "Creditcard"
            }""";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();

    private BankartPaymentProvider provider() {
        return new BankartPaymentProvider(
                client(),
                new NotificationVerifier(SHARED_SECRET, BodyDigest.SHA512, Duration.ofSeconds(60), clock),
                clock);
    }

    // ------------------------------------------------------------------ identity

    @Test
    void identityAndCapabilities() {
        BankartPaymentProvider provider = provider();

        assertThat(provider.id()).isEqualTo("bankart");
        assertThat(provider.capabilities()).containsExactlyInAnyOrder(
                ProviderCapability.HOSTED_CHECKOUT,
                ProviderCapability.RECURRING_CHECKOUT,
                ProviderCapability.WEBHOOK_SIGNATURE,
                ProviderCapability.REFUND,
                ProviderCapability.SUBSCRIPTIONS,
                ProviderCapability.PLAN_CHANGE,
                ProviderCapability.RECONCILE,
                ProviderCapability.TOKENIZATION);
        // No field anywhere in the API defers a cancellation, and no non-browser payment rail exists.
        assertThat(provider.supports(ProviderCapability.CANCEL_AT_PERIOD_END)).isFalse();
        assertThat(provider.supports(ProviderCapability.MACHINE_PAYMENT_URL)).isFalse();
        assertThatNullPointerException().isThrownBy(() -> new BankartPaymentProvider(null, null));
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void oneOffCheckoutSendsADebit() throws Exception {
        stubTransaction("debit", REDIRECT);

        RedirectTarget target = provider().startCheckout(
                PaymentIntent.builder("order-1")
                        .amount(Money.of("9.99", "EUR"))
                        .description("Premium Plan")
                        .urls("https://k/s", "https://k/c", "https://k/e", "https://k/cb")
                        .customer(Customer.builder().reference("u-1").email("a@example.com")
                                .name("Ана Петровска").countryCode("MK").vatNumber("MK4030000000000").build())
                        .metadata(Map.of("listing", "42"))
                        .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("debit"));
        assertSignature(request, "POST", transactionPath("debit"));

        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("order-1");
        assertThat(body.get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("description").asText()).isEqualTo("Premium Plan");
        assertThat(body.get("successUrl").asText()).isEqualTo("https://k/s");
        assertThat(body.get("callbackUrl").asText()).isEqualTo("https://k/cb");
        assertThat(body.get("extraData").get("listing").asText()).isEqualTo("42");
        assertThat(body.get("customer").get("identification").asText()).isEqualTo("u-1");
        assertThat(body.get("customer").get("firstName").asText()).isEqualTo("Ана");
        assertThat(body.get("customer").get("lastName").asText()).isEqualTo("Петровска");
        assertThat(body.get("customer").get("billingCountry").asText()).isEqualTo("MK");
        assertThat(body.has("schedule")).isFalse();
        assertThat(body.has("withRegister")).isFalse();

        assertThat(target.redirectUrl()).isEqualTo("https://gateway.example/pay/abcde12345");
        assertThat(target.checkoutRef()).isEqualTo("abcde12345abcde12345");
        assertThat(target.iframe()).isFalse();
        assertThat(target.machinePayment()).isEmpty();
    }

    @Test
    @DisplayName("a one-word customer name goes in firstName rather than being split at nothing")
    void singleWordCustomerName() throws Exception {
        stubTransaction("debit", REDIRECT);

        provider().startCheckout(PaymentIntent.builder("order-1")
                .amount(Money.of("1.00", "EUR"))
                .customer(Customer.withEmail("a@example.com"))
                .build());
        JsonNode noName = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(noName.get("customer").has("firstName")).isFalse();
        assertThat(noName.get("customer").get("email").asText()).isEqualTo("a@example.com");

        gateway.resetRequests();
        provider().startCheckout(PaymentIntent.builder("order-2")
                .amount(Money.of("1.00", "EUR"))
                .customer(Customer.builder().name("Prince").build())
                .build());
        JsonNode oneWord = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(oneWord.get("customer").get("firstName").asText()).isEqualTo("Prince");
        assertThat(oneWord.get("customer").has("lastName")).isFalse();
    }

    @Test
    @DisplayName("an iframe redirect is reported as one")
    void iframeRedirect() {
        stubTransaction("debit", REDIRECT.replace("\"fullpage\"", "\"iframe\""));

        assertThat(provider().startCheckout(
                PaymentIntent.builder("order-1").amount(Money.of("1.00", "EUR")).build()).iframe()).isTrue();
    }

    @Test
    void recurringCheckoutRegistersAndAttachesTheSchedule() throws Exception {
        stubTransaction("debit", REDIRECT);

        provider().startCheckout(PaymentIntent.builder("sub-1")
                .amount(Money.of("9.99", "EUR"))
                .recurrence(Recurrence.monthly().startingAt(Instant.parse("2020-08-01T00:00:00Z")))
                .callbackUrl("https://k/cb")
                .build());

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("withRegister").asBoolean())
                .as("without it the schedule has nothing to charge on the second cycle").isTrue();
        assertThat(body.get("transactionIndicator").asText()).isEqualTo("INITIAL");
        assertThat(body.get("schedule").get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("schedule").get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("schedule").get("periodLength").asInt()).isEqualTo(1);
        assertThat(body.get("schedule").get("periodUnit").asText()).isEqualTo("MONTH");
        assertThat(body.get("schedule").get("startDateTime").asText()).isEqualTo("2020-08-01T00:00:00+00:00");
        assertThat(body.get("schedule").get("callbackUrl").asText()).isEqualTo("https://k/cb");
    }

    @Test
    void everyRecurrenceUnitHasAWireSpelling() throws Exception {
        for (Map.Entry<PeriodUnit, String> expected : Map.of(
                PeriodUnit.DAY, "DAY", PeriodUnit.WEEK, "WEEK",
                PeriodUnit.MONTH, "MONTH", PeriodUnit.YEAR, "YEAR").entrySet()) {
            gateway.resetRequests();
            stubTransaction("debit", REDIRECT);

            provider().startCheckout(PaymentIntent.builder("sub-1")
                    .amount(Money.of("1.00", "EUR"))
                    .recurrence(Recurrence.every(2, expected.getKey()))
                    .build());

            JsonNode schedule = mapper.readTree(onlyRequest().getBodyAsString()).get("schedule");
            assertThat(schedule.get("periodUnit").asText()).isEqualTo(expected.getValue());
            assertThat(schedule.get("periodLength").asInt()).isEqualTo(2);
            assertThat(schedule.has("startDateTime")).isFalse();
        }
    }

    @Test
    @DisplayName("a recurring intent carrying only a planRef is refused by name")
    void planRefAloneCannotBeServed() {
        PaymentIntent intent = PaymentIntent.builder("sub-1").planRef("premium-monthly").build();

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(intent))
                .withMessageContaining("no plan catalogue")
                .withMessageContaining("premium-monthly")
                .satisfies(e -> {
                    assertThat(e.provider()).isEqualTo("bankart");
                    assertThat(e.outcomeUnknown()).isFalse();
                });
    }

    @Test
    void aRecurrenceWithNoAmountIsRefused() {
        PaymentIntent intent = PaymentIntent.builder("sub-1")
                .planRef("premium-monthly").recurrence(Recurrence.monthly()).build();

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(intent))
                .withMessageContaining("requires its own amount and currency");
    }

    @Test
    @DisplayName("a trial is refused rather than approximated with a deferred first charge")
    void trialsAreRefused() {
        PaymentIntent intent = PaymentIntent.builder("sub-1")
                .amount(Money.of("9.99", "EUR"))
                .recurrence(Recurrence.monthly().withTrialDays(14))
                .build();

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(intent))
                .withMessageContaining("no trial");
    }

    @Test
    void aDeclineBecomesAPaymentDeclinedException() {
        stubTransaction("debit", """
                {
                  "success": false,
                  "uuid": "abcde12345abcde12345",
                  "returnType": "ERROR",
                  "errors": [{"errorMessage": "Stolen card", "errorCode": 2016,
                              "adapterMessage": "declined", "adapterCode": "05"}]
                }""");

        assertThatExceptionOfType(PaymentDeclinedException.class)
                .isThrownBy(() -> provider().startCheckout(
                        PaymentIntent.builder("order-1").amount(Money.of("1.00", "EUR")).build()))
                .satisfies(e -> {
                    assertThat(e.provider()).isEqualTo("bankart");
                    assertThat(e.declineCode()).isEqualTo("2016");
                });
    }

    @Test
    @DisplayName("a rejected request becomes a provider exception carrying the gateway's own code")
    void aGeneralErrorBecomesAProviderException() {
        stubTransaction("debit", "{\"success\":false,\"errorMessage\":\"Invalid signature\",\"errorCode\":1004}");

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(
                        PaymentIntent.builder("order-1").amount(Money.of("1.00", "EUR")).build()))
                .satisfies(e -> {
                    assertThat(e.code()).isEqualTo("1004");
                    assertThat(e.outcomeUnknown()).isFalse();
                });
    }

    @Test
    @DisplayName("a transport failure is the one case whose outcome is unknown")
    void aTransportFailureIsOutcomeUnknown() {
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(
                        PaymentIntent.builder("order-1").amount(Money.of("1.00", "EUR")).build()))
                .satisfies(e -> assertThat(e.outcomeUnknown())
                        .as("retrying a payment that may have gone through charges the buyer twice")
                        .isTrue());
    }

    @Test
    @DisplayName("a connector that answers FINISHED instead of REDIRECT is a provider exception, not a null")
    void aNonRedirectAnswerIsRefused() {
        stubTransaction("debit", "{\"success\":true,\"uuid\":\"u\",\"returnType\":\"FINISHED\"}");

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().startCheckout(
                        PaymentIntent.builder("order-1").amount(Money.of("1.00", "EUR")).build()))
                .satisfies(e -> assertThat(e.outcomeUnknown()).isFalse());
    }

    // ------------------------------------------------------------------ webhooks

    @Test
    void aSuccessfulDebitBecomesPaymentSucceeded() {
        PaymentEvent event = handle("notification-success.json");

        assertThat(event).isInstanceOf(PaymentSucceeded.class);
        PaymentSucceeded succeeded = (PaymentSucceeded) event;
        assertThat(succeeded.paymentRef()).isEqualTo("abcde12345abcde12345");
        assertThat(succeeded.merchantReference()).isEqualTo("2019-09-02-0007");
        assertThat(succeeded.amount()).isEqualTo(Money.of("9.99", "EUR"));
        assertThat(succeeded.eventId()).isEqualTo("abcde12345abcde12345");
        assertThat(succeeded.acknowledgement()).isEqualTo("OK");
        assertThat(succeeded.occurredAt()).isEqualTo(NOW);
        assertThat(succeeded.header().provider()).isEqualTo("bankart");
        assertThat(succeeded.header().rawPayload()).contains("abcde12345abcde12345");
    }

    @Test
    @DisplayName("a capture moves money; a preauthorize or an incremental authorization does not")
    void onlySettlingTypesBecomePaymentSucceeded() {
        String body = """
                {"result":"OK","uuid":"u-1","merchantTransactionId":"tx-1","transactionType":"CAPTURE",
                 "amount":"9.99","currency":"EUR"}""";

        assertThat(handleBody(body)).isInstanceOf(PaymentSucceeded.class);
        assertThat(handleBody(body.replace("CAPTURE", "PREAUTHORIZE")))
                .isInstanceOfSatisfying(UnknownEvent.class,
                        unknown -> assertThat(unknown.providerEventType()).isEqualTo("OK:PREAUTHORIZE"));
        assertThat(handleBody(body.replace("CAPTURE", "INCREMENTAL-AUTHORIZATION")))
                .isInstanceOfSatisfying(UnknownEvent.class, unknown ->
                        assertThat(unknown.providerEventType()).isEqualTo("OK:INCREMENTAL_AUTHORIZATION"));
        assertThat(handleBody("{\"result\":\"OK\",\"uuid\":\"u-2\"}"))
                .isInstanceOf(UnknownEvent.class);
    }

    @Test
    void aFailedDebitBecomesPaymentFailed() {
        PaymentEvent event = handle("notification-error.json");

        assertThat(event).isInstanceOfSatisfying(PaymentFailed.class, failed -> {
            assertThat(failed.paymentRef()).isNotBlank();
            assertThat(failed.subscriptionRef()).isNull();
            assertThat(failed.declineCode()).isNotBlank();
        });
    }

    @Test
    void chargebacksAndReversalsMapToTheirOwnEvents() {
        assertThat(handle("notification-chargeback.json"))
                .isInstanceOfSatisfying(ChargebackOpened.class, opened -> {
                    assertThat(opened.paymentRef()).isNotBlank();
                    assertThat(opened.chargebackRef()).isNotBlank();
                });
        assertThat(handle("notification-chargeback-reversal.json"))
                .isInstanceOfSatisfying(ChargebackReversed.class, reversed ->
                        assertThat(reversed.paymentRef()).isNotBlank());
    }

    @Test
    @DisplayName("a chargeback with no chargebackData still produces the event, off the notification itself")
    void chargebackWithoutItsData() {
        String body = """
                {"result":"OK","uuid":"cb-1","merchantTransactionId":"tx-1",
                 "transactionType":"CHARGEBACK","amount":"9.99","currency":"EUR","message":"Fraud"}""";

        assertThat(handleBody(body)).isInstanceOfSatisfying(ChargebackOpened.class, opened -> {
            assertThat(opened.paymentRef()).isEqualTo("cb-1");
            assertThat(opened.amount()).isEqualTo(Money.of("9.99", "EUR"));
            assertThat(opened.reason()).isEqualTo("Fraud");
            assertThat(opened.merchantReference()).isEqualTo("tx-1");
        });

        assertThat(handleBody(body.replace("CHARGEBACK", "CHARGEBACK-REVERSAL")))
                .isInstanceOfSatisfying(ChargebackReversed.class, reversed -> {
                    assertThat(reversed.paymentRef()).isEqualTo("cb-1");
                    assertThat(reversed.chargebackRef()).isEqualTo("cb-1");
                });
    }

    @Test
    void aRefundNotificationBecomesRefunded() {
        String body = """
                {"result":"OK","uuid":"rf-1","merchantTransactionId":"tx-refund",
                 "transactionType":"REFUND","amount":"4.99","currency":"EUR","message":"Goodwill"}""";

        assertThat(handleBody(body)).isInstanceOfSatisfying(Refunded.class, refunded -> {
            assertThat(refunded.refundRef()).isEqualTo("rf-1");
            assertThat(refunded.amount()).isEqualTo(Money.of("4.99", "EUR"));
            assertThat(refunded.reason()).isEqualTo("Goodwill");
        });
    }

    @Test
    @DisplayName("a completed hosted registration is the one checkout completion Bankart can signal")
    void aRegisterBecomesCheckoutCompleted() {
        String body = """
                {"result":"OK","uuid":"reg-1","merchantTransactionId":"order-9","transactionType":"REGISTER"}""";

        assertThat(handleBody(body)).isInstanceOfSatisfying(CheckoutCompleted.class, completed -> {
            assertThat(completed.checkoutRef()).isEqualTo("reg-1");
            assertThat(completed.merchantReference()).isEqualTo("order-9");
            assertThat(completed.paymentRef()).isEqualTo("reg-1");
        });
    }

    @Test
    @DisplayName("a schedule's own charge arrives as a plain DEBIT and is told apart only by scheduleData")
    void aScheduledDebitBecomesSubscriptionRenewed() {
        assertThat(handle("notification-schedule-debit.json"))
                .isInstanceOfSatisfying(SubscriptionRenewed.class, renewed -> {
                    assertThat(renewed.subscriptionRef()).isEqualTo(SCHEDULE_ID);
                    assertThat(renewed.status()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(renewed.currentPeriodEnd()).isEqualTo(Instant.parse("2019-10-30T12:00:00Z"));
                    assertThat(renewed.amount()).isEqualTo(Money.of("9.99", "EUR"));
                    assertThat(renewed.paymentRef()).isEqualTo("cdefa34567cdefa34567");
                });
    }

    @Test
    void aScheduledRegisterBecomesSubscriptionCreated() {
        assertThat(handle("notification-schedule-register.json"))
                .isInstanceOfSatisfying(SubscriptionCreated.class, created -> {
                    assertThat(created.subscriptionRef()).isEqualTo(SCHEDULE_ID);
                    assertThat(created.status()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(created.currentPeriodEnd()).isEqualTo(Instant.parse("2019-09-30T12:00:00Z"));
                    assertThat(created.merchantReference()).isEqualTo("2019-09-02-0020");
                });
    }

    @Test
    void aCancelledScheduleBecomesSubscriptionCancelled() {
        assertThat(handle("notification-schedule-cancelled.json"))
                .isInstanceOfSatisfying(SubscriptionCancelled.class, cancelled -> {
                    assertThat(cancelled.subscriptionRef()).isEqualTo(SCHEDULE_ID);
                    assertThat(cancelled.status()).isEqualTo(SubscriptionStatus.CANCELLED);
                    assertThat(cancelled.timing())
                            .isEqualTo(net.aetherealtech.payments.EffectiveTiming.IMMEDIATE);
                    assertThat(cancelled.effectiveAt()).isEqualTo(NOW);
                    assertThat(cancelled.reason()).isEqualTo("Schedule ended");
                });
    }

    @Test
    @DisplayName("a paused schedule has no event in this SPI and must not be reported as a cancellation")
    void aPausedScheduleBecomesUnknown() {
        assertThat(handle("notification-schedule-paused.json"))
                .isInstanceOfSatisfying(UnknownEvent.class,
                        unknown -> assertThat(unknown.providerEventType()).isEqualTo("schedule-paused"));
    }

    @Test
    void aFailedScheduledChargeCarriesTheSubscriptionAndItsStatus() {
        assertThat(handle("notification-schedule-failed.json"))
                .isInstanceOfSatisfying(PaymentFailed.class, failed -> {
                    assertThat(failed.subscriptionRef()).isEqualTo(SCHEDULE_ID);
                    assertThat(failed.subscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
                    assertThat(failed.declineCode()).isEqualTo("2016");
                    assertThat(failed.reason()).isEqualTo("The transaction was declined");
                    assertThat(failed.amount()).isEqualTo(Money.of("9.99", "EUR"));
                });
    }

    @Test
    @DisplayName("a pending notification is kept as a verified fact rather than guessed at")
    void pendingBecomesUnknown() {
        assertThat(handleBody("{\"result\":\"PENDING\",\"uuid\":\"p-1\",\"transactionType\":\"DEBIT\"}"))
                .isInstanceOfSatisfying(UnknownEvent.class,
                        unknown -> assertThat(unknown.providerEventType()).isEqualTo("PENDING:DEBIT"));

        assertThat(handleBody("{\"uuid\":\"p-2\"}"))
                .isInstanceOfSatisfying(UnknownEvent.class,
                        unknown -> assertThat(unknown.providerEventType()).isEqualTo("UNKNOWN:UNKNOWN"));

        assertThat(handleBody("""
                {"result":"PENDING","uuid":"p-3","transactionType":"DEBIT",
                 "scheduleData":{"scheduleId":"SC-1","scheduleStatus":"ACTIVE"}}"""))
                .isInstanceOfSatisfying(UnknownEvent.class,
                        unknown -> assertThat(unknown.providerEventType()).isEqualTo("PENDING:DEBIT"));
    }

    @Test
    @DisplayName("a callback with no uuid keeps an id stable across redeliveries, and stays uninterpreted")
    void synthesisedEventId() {
        // Every typed event is built around the gateway's uuid. Without one there is no payment ref
        // to carry, so the verified fact is kept as an UnknownEvent rather than guessed into shape.
        PaymentEvent typed = handleBody(
                "{\"result\":\"OK\",\"merchantTransactionId\":\"tx-7\",\"transactionType\":\"DEBIT\"}");
        assertThat(typed.eventId()).isEqualTo("DEBIT:tx-7");
        assertThat(typed).isInstanceOf(UnknownEvent.class);
        assertThat(handleBody("{\"result\":\"OK\",\"merchantTransactionId\":\"tx-7\"}").eventId())
                .isEqualTo("UNKNOWN:tx-7");
    }

    @Test
    @DisplayName("a Date the gateway spells with UTC rather than GMT still dates the event")
    void utcSpelledDateHeader() {
        String body = "{\"result\":\"OK\",\"uuid\":\"u-1\",\"transactionType\":\"DEBIT\"}";
        String date = "Tue, 21 Jul 2020 13:15:03 UTC";

        assertThat(handle(body, date, headers(body, date)).occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("with no usable scheduledAt the charge is reported as a payment, never as a renewal")
    void unparseableScheduledAt() {
        // SubscriptionRenewed exists to say how long access is owed for; a renewal with an invented
        // period end would be worse than none, so the money is reported without the claim.
        assertThat(handleBody("""
                {"result":"OK","uuid":"u-1","transactionType":"DEBIT","amount":"9.99","currency":"EUR",
                 "scheduleData":{"scheduleId":"SC-1","scheduleStatus":"ACTIVE","scheduledAt":"soon"}}"""))
                .isInstanceOfSatisfying(PaymentSucceeded.class, succeeded -> {
                    assertThat(succeeded.subscriptionRef()).isEqualTo("SC-1");
                    assertThat(succeeded.amount()).isEqualTo(Money.of("9.99", "EUR"));
                });
        assertThat(handleBody("""
                {"result":"OK","uuid":"u-2","transactionType":"DEBIT",
                 "scheduleData":{"scheduleId":"SC-1","scheduleStatus":"ACTIVE"}}"""))
                .isInstanceOf(PaymentSucceeded.class);
    }

    // ------------------------------------------------------------------ webhook refusals

    @Test
    void anUnsignedWebhookIsRefusedBeforeAnythingIsRead() {
        String body = Fixtures.load("notification-success.json");
        Map<String, String> headers = new LinkedHashMap<>(headers(body, DATE));
        headers.remove("X-Signature");

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers)))
                .satisfies(e -> {
                    assertThat(e.reason()).isEqualTo("missing-signature-header");
                    assertThat(e.provider()).isEqualTo("bankart");
                });
    }

    @Test
    void aBlankSignatureIsTreatedAsAMissingOne() {
        String body = Fixtures.load("notification-success.json");
        Map<String, String> headers = new LinkedHashMap<>(headers(body, DATE));
        headers.put("X-Signature", "   ");

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers)))
                .satisfies(e -> assertThat(e.reason()).isEqualTo("missing-signature-header"));
    }

    @Test
    void aWebhookWithNoDateIsRefused() {
        String body = Fixtures.load("notification-success.json");
        Map<String, String> headers = new LinkedHashMap<>(headers(body, DATE));
        headers.remove("Date");
        headers.remove("X-Date");

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers)))
                .satisfies(e -> assertThat(e.reason()).isEqualTo("missing-date-header"));
    }

    @Test
    @DisplayName("a tampered body is refused with the component that failed named")
    void aTamperedBodyIsRefused() {
        String genuine = Fixtures.load("notification-success.json");
        Map<String, String> headers = headers(genuine, DATE);
        String tampered = genuine.replace("\"9.99\"", "\"0.01\"");

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers)))
                .satisfies(e -> assertThat(e.reason()).isEqualTo("signature-mismatch"));
    }

    @Test
    void aDateOutsideTheWindowIsRefused() {
        String body = Fixtures.load("notification-success.json");
        String stale = HmacSigner.formatDate(NOW.minusSeconds(3600));

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers(body, stale))))
                .satisfies(e -> assertThat(e.reason()).isEqualTo("date-outside-window"));
    }

    @Test
    void anUnparseableDateIsRefused() {
        String body = Fixtures.load("notification-success.json");

        assertThatExceptionOfType(WebhookVerificationException.class)
                .isThrownBy(() -> provider().handleWebhook(
                        InboundWebhook.post(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                CALLBACK_URI, headers(body, "yesterday afternoon"))))
                .satisfies(e -> assertThat(e.reason()).isEqualTo("date-unparseable"));
    }

    @Test
    @DisplayName("headers are found however the proxy in front cased them")
    void headerLookupIsCaseInsensitive() {
        String body = "{\"result\":\"OK\",\"uuid\":\"u-1\",\"transactionType\":\"DEBIT\"}";
        SignedRequest signed = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);
        Map<String, String> lowercased = Map.of(
                "content-type", CONTENT_TYPE,
                "date", DATE,
                "x-signature", new HmacSigner(SHARED_SECRET).sign(signed));

        assertThat(provider().handleWebhook(InboundWebhook.post(
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), CALLBACK_URI, lowercased)))
                .isInstanceOf(PaymentSucceeded.class);
    }

    @Test
    void nullArgumentsAreRefusedAtTheBoundary() {
        BankartPaymentProvider provider = provider();

        assertThatNullPointerException().isThrownBy(() -> provider.startCheckout(null));
        assertThatNullPointerException().isThrownBy(() -> provider.handleWebhook(null));
        assertThatNullPointerException().isThrownBy(() -> provider.refund(null));
        assertThatIllegalArgumentException().isThrownBy(() -> provider.reconcile(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> provider.cancelSubscription(null, false));
    }

    // ------------------------------------------------------------------ reconcile

    @Test
    void reconcileReadsTheScheduleAndMapsItsStatus() {
        stubScheduleGet(Fixtures.load("schedule-get-active.json"));

        SubscriptionSnapshot snapshot = provider().reconcile(SCHEDULE_ID);

        assertThat(snapshot.subscriptionRef()).isEqualTo(SCHEDULE_ID);
        assertThat(snapshot.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(Instant.parse("2019-09-30T12:00:00Z"));
        assertThat(snapshot.activeUntil()).contains(Instant.parse("2019-09-30T12:00:00Z"));
        assertThat(snapshot.customerRef()).isEqualTo("abcde01234abcde01234");
        assertThat(snapshot.observedAt()).isEqualTo(NOW);
        assertThat(snapshot.cancelAtPeriodEnd()).isFalse();
        assertThat(snapshot.effectiveCancelDate()).isNull();
    }

    @Test
    void everyScheduleStatusHasASubscriptionStatus() {
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.ACTIVE))
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.PAUSED))
                .isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.CANCELLED))
                .isEqualTo(SubscriptionStatus.CANCELLED);
        // Alive but not billing: not UNKNOWN, which is reserved for a status this SPI does not model,
        // and not CANCELLED, which would revoke access over something Bankart has not called terminal.
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.ERROR))
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.CREATE_PENDING))
                .isEqualTo(SubscriptionStatus.INCOMPLETE);
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.NON_EXISTING))
                .isEqualTo(SubscriptionStatus.UNKNOWN);
        assertThat(BankartPaymentProvider.subscriptionStatus(ScheduleStatus.UNKNOWN))
                .isEqualTo(SubscriptionStatus.UNKNOWN);
        assertThat(BankartPaymentProvider.subscriptionStatus(null)).isEqualTo(SubscriptionStatus.UNKNOWN);
    }

    @Test
    void aRefusedReconcileCarriesTheScheduleErrorCode() {
        stubScheduleGet(Fixtures.load("schedule-error-7040.json"));

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().reconcile(SCHEDULE_ID))
                .withMessageContaining("The scheduleId is not valid")
                .satisfies(e -> assertThat(e.code()).isEqualTo("7040"));
    }

    @Test
    void aRefusalWithNoMessageStillReads() {
        stubScheduleGet("{\"success\":false}");

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().reconcile(SCHEDULE_ID))
                .withMessageContaining("no reason given")
                .satisfies(e -> assertThat(e.code()).isNull());
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    void cancelEndsTheScheduleImmediately() {
        gateway.stubFor(post(urlEqualTo("/api/v3/schedule/" + API_KEY + "/" + SCHEDULE_ID + "/cancel"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.load("schedule-cancelled.json"))));

        SubscriptionSnapshot snapshot = provider().cancelSubscription(SCHEDULE_ID, false);

        assertThat(snapshot.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(snapshot.effectiveCancelDate()).isEqualTo(NOW);
        assertThat(snapshot.currentPeriodEnd()).as("a cancelled schedule answers with no next charge").isNull();
        assertThat(onlyRequest().getBodyAsString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("cancelling at period end is refused by name, not approximated by cancelling now")
    void cancelAtPeriodEndIsUnsupported() {
        assertThatExceptionOfType(UnsupportedCapabilityException.class)
                .isThrownBy(() -> provider().cancelSubscription(SCHEDULE_ID, true))
                .satisfies(e -> {
                    assertThat(e.capability()).isEqualTo(ProviderCapability.CANCEL_AT_PERIOD_END);
                    assertThat(e.provider()).isEqualTo("bankart");
                });
        assertThat(gateway.findAll(com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(com.github.tomakehurst.wiremock.matching.UrlPattern.ANY)))
                .as("nothing may reach the gateway on a refusal").isEmpty();
    }

    @Test
    void aRefusedCancelIsRaised() {
        gateway.stubFor(post(urlEqualTo("/api/v3/schedule/" + API_KEY + "/" + SCHEDULE_ID + "/cancel"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.load("schedule-error-7070.json"))));

        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().cancelSubscription(SCHEDULE_ID, false))
                .satisfies(e -> assertThat(e.code()).isEqualTo("7070"));
    }

    // ------------------------------------------------------------------ refunds

    @Test
    void aPartialRefundSendsTheDocumentedBody() throws Exception {
        stubTransaction("refund", """
                {"success":true,"uuid":"rf-1","returnType":"FINISHED","paymentMethod":"Creditcard"}""");

        RefundReceipt receipt = provider().refund(
                RefundRequest.partial("bcdef23456bcdef23456", Money.of("4.99", "EUR"))
                        .withMerchantReference("refund-1"));

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("refund-1");
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.get("amount").asText()).isEqualTo("4.99");
        assertThat(body.get("currency").asText()).isEqualTo("EUR");

        assertThat(receipt.refundRef()).isEqualTo("rf-1");
        assertThat(receipt.paymentRef()).isEqualTo("bcdef23456bcdef23456");
        assertThat(receipt.amount()).isEqualTo(Money.of("4.99", "EUR"));
        assertThat(receipt.pending()).isFalse();
        assertThat(receipt.acceptedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a refund the gateway has taken but not settled is reported as pending")
    void aPendingRefund() {
        stubTransaction("refund", "{\"success\":true,\"uuid\":\"rf-2\",\"returnType\":\"PENDING\"}");

        assertThat(provider().refund(RefundRequest.partial("ref", Money.of("1.00", "EUR"))
                .withMerchantReference("refund-2")).pending()).isTrue();
    }

    @Test
    @DisplayName("a full refund is refused: Bankart's refund requires an explicit amount")
    void aFullRefundCannotBeExpressed() {
        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().refund(RefundRequest.full("ref")))
                .withMessageContaining("explicit amount");
    }

    @Test
    void aRefundWithoutAMerchantReferenceIsRefused() {
        assertThatExceptionOfType(PaymentProviderException.class)
                .isThrownBy(() -> provider().refund(RefundRequest.partial("ref", Money.of("1.00", "EUR"))))
                .withMessageContaining("merchantTransactionId");
    }

    @Test
    void aRefusedRefundIsADecline() {
        stubTransaction("refund", """
                {"success":false,"uuid":"rf-3","returnType":"ERROR",
                 "errors":[{"errorMessage":"Already refunded","errorCode":3005}]}""");

        assertThatExceptionOfType(net.aetherealtech.payments.exception.PaymentException.class)
                .isThrownBy(() -> provider().refund(RefundRequest.partial("ref", Money.of("1.00", "EUR"))
                        .withMerchantReference("refund-3")));
    }

    // ------------------------------------------------------------------ helpers

    private void stubScheduleGet(String body) {
        gateway.stubFor(get(urlEqualTo("/api/v3/schedule/" + API_KEY + "/" + SCHEDULE_ID + "/get"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private PaymentEvent handle(String fixture) {
        return handleBody(Fixtures.load(fixture));
    }

    private PaymentEvent handleBody(String body) {
        return handle(body, DATE, headers(body, DATE));
    }

    private PaymentEvent handle(String body, String date, Map<String, String> headers) {
        return provider().handleWebhook(InboundWebhook.post(
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), CALLBACK_URI, headers));
    }

    private static Map<String, String> headers(String body, String date) {
        SignedRequest signed = SignedRequest.post(body, CONTENT_TYPE, date, CALLBACK_URI);
        return Map.of(
                "Content-Type", CONTENT_TYPE,
                "Date", date,
                "X-Date", date,
                "X-Signature", new HmacSigner(SHARED_SECRET).sign(signed));
    }
}
