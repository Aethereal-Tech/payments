package net.aetherealtech.payments.agentaos;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.aetherealtech.payments.exception.PaymentProviderException;

/** Every refusal AgentaOS can state, and every failure to state one, in the SPI's own exception. */
class AgentaOsClientErrorsTest extends GatewayTestBase {

    private static final String LINK = "/api/v1/gateway/payment-links/pl_9d3";

    @ParameterizedTest
    @CsvSource({
            "400,validation_error",
            "401,authentication_error",
            "403,permission_error",
            "404,not_found",
            "409,idempotency_error",
            "429,rate_limit",
            "500,api_error",
            "503,api_error",
            "418,unknown_error"})
    void mapsEveryStatusToTheProvidersOwnCode(final int status, final String code) {
        stubJson(LINK, status, "{\"message\":\"nope\"}");

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        assertThat(e.provider()).isEqualTo("agentaos");
        assertThat(e.code()).isEqualTo(code);
        assertThat(e).hasMessageContaining("nope");
        assertThat(e.outcomeUnknown()).isFalse();
    }

    @Test
    void namesEveryFieldA400ComplainedAbout() {
        stubJson(LINK, 400, Fixtures.load("error-validation.json"));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        assertThat(e).hasMessageContaining("amount is required")
                .hasMessageContaining("[amount: must be a positive number]")
                .hasMessageContaining("[currency: must be EUR or USD]");
    }

    @Test
    void fallsBackToTheErrorFieldThenToItsOwnWords() {
        stubJson(LINK, 403, Fixtures.load("error-fallback.json"));
        assertThat(catchThrowableOfType(PaymentProviderException.class,
                () -> client().retrievePaymentLink("pl_9d3")))
                .hasMessageContaining("organization suspended");

        gateway.resetAll();
        stubJson(LINK, 404, "{}");
        assertThat(catchThrowableOfType(PaymentProviderException.class,
                () -> client().retrievePaymentLink("pl_9d3")))
                .hasMessageContaining("refused the request to retrieve a payment link with HTTP 404");
    }

    @Test
    void survivesAnErrorBodyThatIsNotJsonAtAll() {
        stubJson(LINK, 502, "<html>Bad Gateway</html>");

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        assertThat(e.code()).isEqualTo("api_error");
        assertThat(e).hasMessageContaining("HTTP 502");
    }

    @Test
    void carriesRetryAfterSecondsAndTheRequestIdIntoTheMessage() {
        gateway.stubFor(get(urlEqualTo(LINK)).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", JSON)
                .withHeader("retry-after", "30")
                .withHeader("x-request-id", "req_88")
                .withBody("{\"message\":\"slow down\"}")));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        assertThat(e.code()).isEqualTo("rate_limit");
        assertThat(e).hasMessageContaining("(retry after 30s)").hasMessageContaining("(request req_88)");
    }

    @Test
    void doesNotRetryAServerError() {
        stubJson(LINK, 500, "{\"message\":\"boom\"}");

        catchThrowableOfType(PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        // The SDK retries a 5xx twice with backoff. A library that repeats a request whose answer never
        // arrived is a library that charges twice, so this asks once and says what it does not know.
        assertThat(allRequests()).hasSize(1);
    }

    @Test
    void aTimeoutIsAnUnknownOutcomeRatherThanAFailure() {
        gateway.stubFor(get(urlEqualTo(LINK)).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2_000)
                .withBody(Fixtures.load("payment-link.json"))));
        final AgentaOsClient client = new AgentaOsClient(config().withTimeout(Duration.ofMillis(200)));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client.retrievePaymentLink("pl_9d3"));

        assertThat(e.code()).isEqualTo("timeout_error");
        assertThat(e.outcomeUnknown()).isTrue();
        assertThat(e).hasMessageContaining("ask what happened rather than sending it again");
    }

    @Test
    void anUnreachableHostIsAlsoAnUnknownOutcome() {
        final AgentaOsClient client = new AgentaOsClient(
                AgentaOsConfig.of(URI.create("http://127.0.0.1:1"), API_KEY));

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client.retrievePaymentLink("pl_9d3"));

        assertThat(e.code()).isEqualTo("network_error");
        assertThat(e.outcomeUnknown()).isTrue();
    }

    @Test
    void aSuccessfulResponseThatIsNotAJsonObjectIsRefusedRatherThanHalfRead() {
        stubJson(LINK, 200, "[1,2,3]");

        final PaymentProviderException e = catchThrowableOfType(
                PaymentProviderException.class, () -> client().retrievePaymentLink("pl_9d3"));

        assertThat(e.code()).isEqualTo("malformed_response");
        assertThat(e.outcomeUnknown()).isFalse();
    }

    @Test
    void anEmptyBodyOnASuccessIsReadAsAnEmptyDocument() {
        stubJson(LINK, 204, "");

        assertThat(client().retrievePaymentLink("pl_9d3").id()).isNull();
    }

    @Test
    void sendsASessionTokenAsABearerHeaderInstead() {
        stubGet(LINK, Fixtures.load("payment-link.json"));
        final String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln";

        new AgentaOsClient(AgentaOsConfig.of(URI.create(gateway.baseUrl()), jwt))
                .retrievePaymentLink("pl_9d3");

        assertThat(onlyRequest().getHeader("authorization")).isEqualTo("Bearer " + jwt);
        assertThat(onlyRequest().getHeader("x-api-key")).isNull();
    }
}
