package net.aetherealtech.payments.bankart;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.http.Fault;

import net.aetherealtech.payments.bankart.exception.BankartApiException;
import net.aetherealtech.payments.bankart.exception.BankartException;
import net.aetherealtech.payments.bankart.exception.BankartTransactionException;
import net.aetherealtech.payments.bankart.exception.BankartTransportException;
import net.aetherealtech.payments.bankart.model.PaymentRequest;
import net.aetherealtech.payments.bankart.model.ReturnType;
import net.aetherealtech.payments.bankart.model.StatusResponse;
import net.aetherealtech.payments.bankart.model.TransactionResponse;
import net.aetherealtech.payments.bankart.model.TransactionType;

/**
 * The line this class holds is that a declined card and a broken integration are different events.
 * Conflating them is how a payments client ends up retrying something it should have surfaced, or
 * surfacing something it should have retried.
 */
class BankartClientErrorsTest extends GatewayTestBase {

    private static final PaymentRequest ANY_DEBIT =
            PaymentRequest.builder("tx", new BigDecimal("9.99"), "EUR").build();

    @Test
    @DisplayName("a declined transaction is returned, not thrown")
    void declineIsAnOutcome() {
        stubTransaction("debit", """
                {
                  "success": false,
                  "uuid": "abcde12345abcde12345",
                  "purchaseId": "20190927-abcde12345abcde12345",
                  "returnType": "ERROR",
                  "paymentMethod": "Creditcard",
                  "errors": [
                    {
                      "errorMessage": "Request failed",
                      "errorCode": 1000,
                      "adapterMessage": "Invalid parameters given",
                      "adapterCode": "1234"
                    }
                  ]
                }""");

        TransactionResponse response = client().debit(ANY_DEBIT);

        assertThat(response.isError()).isTrue();
        assertThat(response.returnType()).isEqualTo(ReturnType.ERROR);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).errorCode()).isEqualTo(1000);
        assertThat(response.errors().get(0).errorMessage()).isEqualTo("Request failed");
        assertThat(response.errors().get(0).adapterCode()).isEqualTo("1234");
        assertThat(response.errors().get(0).adapterMessage()).isEqualTo("Invalid parameters given");
    }

    @Test
    @DisplayName("a rejected signature throws, because no retry will fix it")
    void generalErrorThrows() {
        stubTransaction("debit", """
                {
                  "success": false,
                  "errorMessage": "Signature invalid",
                  "errorCode": 1004
                }""");

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .satisfies(e -> {
                    assertThat(e.errorCode()).isEqualTo(1004);
                    assertThat(e.getMessage()).isEqualTo("Signature invalid");
                    assertThat(e.rawBody()).contains("1004");
                });
    }

    @Test
    void reportsRateLimitingByCode() {
        stubTransaction("debit", "{\"success\": false, \"errorMessage\": \"Too many requests\", \"errorCode\": 1009}");

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(1009));
    }

    @Test
    void rejectsANonJsonBody() {
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withStatus(502).withBody("<html>Bad Gateway</html>")));

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .satisfies(e -> assertThat(e.httpStatus()).isEqualTo(502));
    }

    @Test
    void rejectsAJsonBodyThatIsNotAnObject() {
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .withMessageContaining("non-JSON body");
    }

    @Test
    void rejectsAnEmptyBody() {
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withStatus(200).withBody("")));

        assertThatExceptionOfType(BankartApiException.class).isThrownBy(() -> client().debit(ANY_DEBIT));
    }

    @Test
    @DisplayName("a response whose fields cannot be read is an API error, not a silent null")
    void rejectsAnUnreadableResponseShape() {
        stubTransaction("debit", "{\"returnType\":\"FINISHED\",\"errors\":\"not-an-array\"}");

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .withMessageContaining("Could not read");
    }

    @Test
    @DisplayName("a dropped connection is a transport failure: the outcome is unknown, not failed")
    void connectionFaultsAreTransportFailures() {
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatExceptionOfType(BankartTransportException.class)
                .isThrownBy(() -> client().debit(ANY_DEBIT))
                .withMessageContaining("failed");
    }

    @Test
    void timeoutsAreTransportFailures() {
        // The delay is kept short and the client's deadline far shorter, rather than the reverse.
        // A long in-flight response outlives the test and can still be arriving while the next one
        // resets the stubs — the only cross-test race this suite has room for.
        gateway.stubFor(post(urlEqualTo(transactionPath("debit")))
                .willReturn(aResponse().withStatus(200).withFixedDelay(600).withBody("{}")));
        BankartClient impatient = new BankartClient(signingConfig().withTimeout(Duration.ofMillis(50)));

        assertThatExceptionOfType(BankartTransportException.class).isThrownBy(() -> impatient.debit(ANY_DEBIT));
    }

    @Test
    void unreachableHostsAreTransportFailures() {
        BankartClient offline = new BankartClient(BankartConfig.of(
                URI.create("http://localhost:1/api/v3"), API_KEY, USERNAME, PASSWORD));

        assertThatExceptionOfType(BankartTransportException.class)
                .isThrownBy(() -> offline.debit(ANY_DEBIT));
    }

    // ------------------------------------------------------------------ status

    @Test
    void statusByUuidReadsTheDocumentedResponse() {
        gateway.stubFor(get(urlEqualTo("/api/v3/status/" + API_KEY + "/getByUuid/abcde12345abcde12345"))
                .willReturn(aResponse().withStatus(200)
                        .withBody(Fixtures.load("status-success.json"))));

        StatusResponse status = client().statusByUuid("abcde12345abcde12345");

        assertThat(status.success()).isTrue();
        assertThat(status.transactionStatus()).isEqualTo("SUCCESS");
        assertThat(status.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(status.merchantTransactionId()).isEqualTo("2019-09-02-0001");
        // The status example spells this lowercase where every other example uses upper.
        assertThat(status.transactionType()).isEqualTo(TransactionType.DEBIT);
        assertThat(status.amount()).isEqualByComparingTo("9.99");
        assertThat(status.customer().company()).isEqualTo("ACME Corp.");
        // Nested under "creditcardData" here, flat in the notifications. Both must read.
        assertThat(status.cardData().orElseThrow().lastFourDigits()).isEqualTo("4321");
        assertThat(status.extraData()).containsEntry("someKey", "someValue");
        assertThat(status.errors()).isEmpty();
    }

    @Test
    void statusRequestsAreSignedToo() {
        gateway.stubFor(get(urlEqualTo("/api/v3/status/" + API_KEY + "/getByUuid/u1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"success\":true}")));

        client().statusByUuid("u1");

        assertSignature(onlyRequest(), "GET", "/api/v3/status/" + API_KEY + "/getByUuid/u1");
    }

    @Test
    void statusByMerchantTransactionIdReadsTransactionErrors() {
        gateway.stubFor(get(urlEqualTo("/api/v3/status/" + API_KEY
                + "/getByMerchantTransactionId/2019-09-02-0001"))
                .willReturn(aResponse().withStatus(200)
                        .withBody(Fixtures.load("status-transaction-error.json"))));

        StatusResponse status = client().statusByMerchantTransactionId("2019-09-02-0001");

        assertThat(status.success()).as("the lookup worked").isTrue();
        assertThat(status.transactionStatus()).as("the payment did not").isEqualTo("ERROR");
        assertThat(status.errors()).hasSize(1);
        // The status API names these message/code, not errorMessage/errorCode, and sends code as text.
        assertThat(status.errors().get(0).message()).isEqualTo("Payment could not be processed.");
        assertThat(status.errors().get(0).code()).isEqualTo("1234");
        assertThat(status.errors().get(0).adapterCode()).isEqualTo("1000");
    }

    @Test
    @DisplayName("an unknown transaction is error 8001 and throws")
    void statusOfAnUnknownTransaction() {
        gateway.stubFor(get(urlEqualTo("/api/v3/status/" + API_KEY + "/getByUuid/nope"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"success\":false,\"errorMessage\":\"Transaction not found\",\"errorCode\":8001}")));

        assertThatExceptionOfType(BankartApiException.class)
                .isThrownBy(() -> client().statusByUuid("nope"))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(8001));
    }

    @Test
    @DisplayName("a merchant transaction ID with URL-unsafe characters is encoded into the path")
    void encodesPathSegments() {
        gateway.stubFor(get(urlEqualTo("/api/v3/status/" + API_KEY
                + "/getByMerchantTransactionId/order%2F42%20b"))
                .willReturn(aResponse().withStatus(200).withBody("{\"success\":true}")));

        StatusResponse status = client().statusByMerchantTransactionId("order/42 b");

        assertThat(status.success()).isTrue();
    }

    // ------------------------------------------------------------------ exception surface

    @Test
    void transactionExceptionCarriesTheGatewaySResponse() {
        TransactionResponse response = new TransactionResponse(false, "uuid-9", "pid", ReturnType.ERROR,
                null, null, null, null, null, "Creditcard", null, null, null,
                java.util.List.of(new net.aetherealtech.payments.bankart.model.TransactionError(
                        "Stolen card", 2016, "declined", "05")));

        BankartTransactionException exception = new BankartTransactionException(response);

        assertThat(exception.uuid()).isEqualTo("uuid-9");
        assertThat(exception.response()).isSameAs(response);
        assertThat(exception.errors()).hasSize(1);
        assertThat(exception.firstError()).isPresent();
        assertThat(exception.firstError().orElseThrow().errorCode()).isEqualTo(2016);
        assertThat(exception.getMessage()).contains("uuid-9").contains("2016").contains("Stolen card");
    }

    @Test
    @DisplayName("an ERROR with no errors array still produces a usable message")
    void transactionExceptionWithoutDetail() {
        TransactionResponse response = new TransactionResponse(false, "uuid-9", "pid", ReturnType.ERROR,
                null, null, null, null, null, null, null, null, null, null);

        assertThat(new BankartTransactionException(response).getMessage())
                .contains("uuid-9")
                .contains("without a reported error");
    }

    @Test
    void theHierarchyIsRootedInBankartException() {
        assertThat(new BankartApiException("m", 1, 200, "{}")).isInstanceOf(BankartException.class);
        assertThat(new BankartTransportException("m", new java.io.IOException())).isInstanceOf(BankartException.class);
        assertThat(new BankartException("m", new IllegalStateException()).getCause())
                .isInstanceOf(IllegalStateException.class);
    }
}
