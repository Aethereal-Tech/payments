package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.agentaos.model.Checkout;
import net.aetherealtech.payments.agentaos.model.CheckoutStatus;
import net.aetherealtech.payments.agentaos.model.CreateCheckoutRequest;
import net.aetherealtech.payments.agentaos.model.SellerMode;

/** Sessions: the exact path, verb, headers and body, and the exact response fields read back. */
class CheckoutWireTest extends GatewayTestBase {

    private static final String SESSIONS = "/api/v1/gateway/sessions";

    @Test
    void createPostsACamelCaseBodyToTheSessionsPath() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        client().createCheckout(CreateCheckoutRequest.of(Money.of("29.99", "EUR"))
                .description("A thing")
                .buyer("buyer@example.com", "A Buyer", "MK")
                .metadata(Map.of("merchantReference", "order-4471"))
                .webhookUrl("https://shop.example/hooks/agentaos")
                .urls("https://shop.example/thanks", "https://shop.example/cart")
                .expiresIn(1800)
                .supportedNetworks(List.of("eip155:8453"))
                .dueDate(LocalDate.of(2026, 9, 30))
                .build());

        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("POST");
        assertThat(request.getUrl()).isEqualTo(SESSIONS);
        assertThat(request.getBodyAsString()).isEqualTo(
                "{\"amount\":29.99,\"currency\":\"EUR\",\"description\":\"A thing\","
                        + "\"buyerEmail\":\"buyer@example.com\",\"buyerName\":\"A Buyer\",\"buyerCountry\":\"MK\","
                        + "\"metadata\":{\"merchantReference\":\"order-4471\"},"
                        + "\"webhookUrl\":\"https://shop.example/hooks/agentaos\","
                        + "\"successUrl\":\"https://shop.example/thanks\","
                        + "\"cancelUrl\":\"https://shop.example/cart\",\"expiresIn\":1800,"
                        + "\"supportedNetworks\":[\"eip155:8453\"],\"dueDate\":\"2026-09-30\"}");
    }

    @Test
    void createNeverSendsSellerMode() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        client().createCheckout(CreateCheckoutRequest.forLink("pl_9d3").build());

        // Server-derived, and a 2.0.0 gateway answers 400 when a client sends it.
        assertThat(onlyRequest().getBodyAsString())
                .doesNotContain("sellerMode")
                .isEqualTo("{\"linkId\":\"pl_9d3\"}");
    }

    @Test
    void everyPostCarriesAnIdempotencyKeyAndAJsonContentType() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        client().createCheckout(CreateCheckoutRequest.forLink("pl_9d3").build());

        final LoggedRequest request = onlyRequest();
        assertCommonHeaders(request);
        assertThat(request.getHeader("content-type")).isEqualTo(JSON);
        assertThat(UUID.fromString(request.getHeader("idempotency-key"))).isNotNull();
    }

    @Test
    void aCallerSuppliedIdempotencyKeyIsSentAsGiven() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        client().createCheckout(CreateCheckoutRequest.forLink("pl_9d3").build(), "order-4471");

        assertThat(onlyRequest().getHeader("idempotency-key")).isEqualTo("order-4471");
    }

    @Test
    void twoCallsWithNoKeyGetTwoDifferentGeneratedOnes() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));
        final AgentaOsClient client = client();

        client.createCheckout(CreateCheckoutRequest.forLink("pl_9d3").build());
        client.createCheckout(CreateCheckoutRequest.forLink("pl_9d3").build());

        assertThat(allRequests().get(0).getHeader("idempotency-key"))
                .isNotEqualTo(allRequests().get(1).getHeader("idempotency-key"));
    }

    @Test
    void readsTheResponseAsSnakeCase() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        final Checkout checkout = client().createCheckout(
                CreateCheckoutRequest.of(Money.of("29.99", "EUR")).build());

        assertThat(checkout.id()).isEqualTo("chk_01J8Z2Q7T4");
        assertThat(checkout.sessionId()).isEqualTo("cs_live_9Kq2m4");
        assertThat(checkout.orgId()).isEqualTo("org_7fc1");
        assertThat(checkout.paymentLinkId()).isNull();
        assertThat(checkout.checkoutUrl()).isEqualTo("https://pay.agentaos.ai/c/cs_live_9Kq2m4");
        assertThat(checkout.x402Url()).isEqualTo("https://api.agentaos.ai/x402/cs_live_9Kq2m4");
        assertThat(checkout.machinePaymentUrl()).contains("https://api.agentaos.ai/x402/cs_live_9Kq2m4");
        assertThat(checkout.status()).isEqualTo(CheckoutStatus.OPEN);
        assertThat(checkout.sellerMode()).isEqualTo(SellerMode.MOR);
        assertThat(checkout.amountOverride()).isEqualTo(new Money(new BigDecimal("29.99"), "EUR"));
        assertThat(checkout.currency()).isEqualTo("EUR");
        assertThat(checkout.metadata()).containsEntry("merchantReference", "order-4471");
        assertThat(checkout.successUrl()).isEqualTo("https://shop.example/thanks");
        assertThat(checkout.cancelUrl()).isEqualTo("https://shop.example/cart");
        assertThat(checkout.invoiceId()).isNull();
        assertThat(checkout.expiresAt()).isEqualTo(Instant.parse("2026-09-04T13:30:00Z"));
        assertThat(checkout.createdAt()).isEqualTo(Instant.parse("2026-09-04T13:00:00Z"));
        assertThat(checkout.updatedAt()).isEqualTo(Instant.parse("2026-09-04T13:00:00Z"));
    }

    @Test
    void retrieveGetsTheSessionAndSendsNoContentType() {
        stubGet(SESSIONS + "/cs_live_9Kq2m4", Fixtures.load("checkout-created.json"));

        assertThat(client().retrieveCheckout("cs_live_9Kq2m4").sessionId()).isEqualTo("cs_live_9Kq2m4");

        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("GET");
        assertCommonHeaders(request);
        // The SDK sets content-type on a POST with a body and on nothing else.
        assertThat(request.getHeader("content-type")).isNull();
        assertThat(request.getHeader("idempotency-key")).isNull();
    }

    @Test
    void cancelPostsToACancelSubPathWithNoBody() {
        stubJson(SESSIONS + "/cs_live_9Kq2m4/cancel", 200, Fixtures.load("checkout-cancelled.json"));

        final Checkout cancelled = client().cancelCheckout("cs_live_9Kq2m4");

        assertThat(cancelled.status()).isEqualTo(CheckoutStatus.CANCELLED);
        assertThat(cancelled.sellerMode()).isEqualTo(SellerMode.CRYPTO);
        assertThat(cancelled.amountOverride()).isNull();
        assertThat(cancelled.machinePaymentUrl()).isEmpty();

        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("POST");
        assertThat(request.getBodyAsString()).isEmpty();
        assertThat(request.getHeader("content-type")).isNull();
        assertThat(request.getHeader("idempotency-key")).isNotNull();
    }

    @Test
    void percentEncodesAnIdentifierRatherThanLettingItChangeThePath() {
        stubGet(SESSIONS + "/cs%20live%2Fadmin", Fixtures.load("checkout-created.json"));

        client().retrieveCheckout("cs live/admin");

        assertThat(onlyRequest().getUrl()).isEqualTo(SESSIONS + "/cs%20live%2Fadmin");
    }

    @Test
    void refusesABlankIdentifierBeforeBuildingARequest() {
        final AgentaOsClient client = client();

        assertThatThrownBy(() -> client.retrieveCheckout(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId must not be blank");
        assertThatThrownBy(() -> client.cancelCheckout(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(allRequests()).isEmpty();
    }

    @Test
    void refusesARequestThatNamesNeitherALinkNorAnAmount() {
        assertThatThrownBy(() -> new CreateCheckoutRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkId or an amount");
    }

    @Test
    void refusesAnExpiryOutsideTheWindowAgentaOsAccepts() {
        assertThatThrownBy(() -> CreateCheckoutRequest.forLink("pl_9d3").expiresIn(299).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300");
        assertThatThrownBy(() -> CreateCheckoutRequest.forLink("pl_9d3").expiresIn(86_401).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("86400");
    }

    @Test
    void carriesEveryOptionalCheckoutFieldThroughToTheBody() {
        stubJson(SESSIONS, 200, Fixtures.load("checkout-created.json"));

        client().createCheckout(CreateCheckoutRequest.forLink("pl_9d3")
                .taxRateId("tr_18")
                .buyerCompany("Example DOO")
                .buyerAddress("Partizanska 1, Skopje")
                .buyerVat("MK4030000000000")
                .amountOverride(new BigDecimal("9.50"))
                .build());

        assertThat(onlyRequest().getBodyAsString()).isEqualTo(
                "{\"linkId\":\"pl_9d3\",\"taxRateId\":\"tr_18\",\"buyerCompany\":\"Example DOO\","
                        + "\"buyerAddress\":\"Partizanska 1, Skopje\",\"buyerVat\":\"MK4030000000000\","
                        + "\"amountOverride\":9.50}");
    }
}
