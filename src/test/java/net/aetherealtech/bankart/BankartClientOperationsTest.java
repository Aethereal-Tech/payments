package net.aetherealtech.bankart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.bankart.model.CaptureRequest;
import net.aetherealtech.bankart.model.Customer;
import net.aetherealtech.bankart.model.DeregisterRequest;
import net.aetherealtech.bankart.model.PaymentRequest;
import net.aetherealtech.bankart.model.PayoutRequest;
import net.aetherealtech.bankart.model.RefundRequest;
import net.aetherealtech.bankart.model.RegisterRequest;
import net.aetherealtech.bankart.model.ReturnType;
import net.aetherealtech.bankart.model.ThreeDSecureData;
import net.aetherealtech.bankart.model.ThreeDSecureMode;
import net.aetherealtech.bankart.model.TokenType;
import net.aetherealtech.bankart.model.TransactionResponse;
import net.aetherealtech.bankart.model.VoidRequest;

/** One test per documented operation, asserting the request the docs describe and parsing their response. */
class BankartClientOperationsTest extends GatewayTestBase {

    private static final String FINISHED = """
            {
              "success": true,
              "uuid": "abcde12345abcde12345",
              "purchaseId": "20190927-abcde12345abcde12345",
              "returnType": "FINISHED",
              "paymentMethod": "Creditcard"
            }""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void debitPostsTheDocumentedBody() throws Exception {
        stubTransaction("debit", FINISHED);

        TransactionResponse response = client().debit(
                PaymentRequest.builder("2019-09-02-0001", new BigDecimal("9.99"), "EUR")
                        .redirectUrls("https://example.com/success", "https://example.com/cancel",
                                "https://example.com/error", "https://example.com/callback")
                        .description("Example Product")
                        .merchantMetaData("merchantRelevantData")
                        .extraData(Map.of("someKey", "someValue"))
                        .customer(Customer.builder().firstName("John").lastName("Doe").build())
                        .threeDSecureData(ThreeDSecureData.of(ThreeDSecureMode.MANDATORY))
                        .language("en")
                        .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("debit"));
        assertBasicAuth(request);
        assertSignature(request, "POST", transactionPath("debit"));

        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("2019-09-02-0001");
        assertThat(body.get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("amount").isTextual()).as("amounts are strings on the wire").isTrue();
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("successUrl").asText()).isEqualTo("https://example.com/success");
        assertThat(body.get("callbackUrl").asText()).isEqualTo("https://example.com/callback");
        assertThat(body.get("customer").get("firstName").asText()).isEqualTo("John");
        assertThat(body.get("threeDSecureData").get("3dsecure").asText()).isEqualTo("MANDATORY");
        assertThat(body.get("extraData").get("someKey").asText()).isEqualTo("someValue");

        assertThat(response.success()).isTrue();
        assertThat(response.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(response.purchaseId()).isEqualTo("20190927-abcde12345abcde12345");
        assertThat(response.returnType()).isEqualTo(ReturnType.FINISHED);
        assertThat(response.paymentMethod()).isEqualTo("Creditcard");
    }

    @Test
    @DisplayName("a debit may not carry captureInMinutes, which only preauthorize accepts")
    void debitRejectsPreauthorizeOnlyFields() {
        PaymentRequest request = PaymentRequest.builder("tx", new BigDecimal("1.00"), "EUR")
                .captureInMinutes("60").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> client().debit(request))
                .withMessageContaining("captureInMinutes");
    }

    @Test
    void preauthorizeAcceptsCaptureInMinutes() throws Exception {
        stubTransaction("preauthorize", FINISHED);

        client().preauthorize(PaymentRequest.builder("2019-09-02-0002", new BigDecimal("9.99"), "EUR")
                .captureInMinutes("60")
                .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("preauthorize"));
        assertThat(mapper.readTree(request.getBodyAsString()).get("captureInMinutes").asText()).isEqualTo("60");
        assertSignature(request, "POST", transactionPath("preauthorize"));
    }

    @Test
    void capturePostsTheReferenceAndAmount() throws Exception {
        stubTransaction("capture", FINISHED);

        client().capture(CaptureRequest.of("2019-09-02-0003", "bcdef23456bcdef23456",
                new BigDecimal("9.99"), "EUR"));

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("2019-09-02-0003");
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("a void carries only the reference, per the docs' example body")
    void voidPostsOnlyTheReference() throws Exception {
        stubTransaction("void", FINISHED);

        client().voidTransaction(VoidRequest.of("2019-09-02-0004", "bcdef23456bcdef23456"));

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("2019-09-02-0004");
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.has("amount")).isFalse();
        assertThat(body.has("currency")).isFalse();
    }

    @Test
    void partialVoidSendsAnAmount() throws Exception {
        stubTransaction("void", FINISHED);

        client().voidTransaction(new VoidRequest("tx", "ref", new BigDecimal("4.00"), "EUR",
                null, null, null, null));

        assertThat(mapper.readTree(onlyRequest().getBodyAsString()).get("amount").asText()).isEqualTo("4.00");
    }

    @Test
    void refundPostsTheDocumentedBody() throws Exception {
        stubTransaction("refund", FINISHED);

        client().refund(new RefundRequest("2019-09-02-0007", "bcdef23456bcdef23456",
                new BigDecimal("9.99"), "EUR", null, null, null, null,
                "https://example.com/callback", null, "Refund money"));

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("refund"));
        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.get("callbackUrl").asText()).isEqualTo("https://example.com/callback");
        assertThat(body.get("description").asText()).isEqualTo("Refund money");
        assertSignature(request, "POST", transactionPath("refund"));
    }

    @Test
    @DisplayName("a partial capture reports what is left in extraData.remainingAmount")
    void readsRemainingAmount() {
        stubTransaction("capture", """
                {
                  "success": true,
                  "uuid": "abcde12345abcde12345",
                  "purchaseId": "20190927-abcde12345abcde12345",
                  "returnType": "FINISHED",
                  "paymentMethod": "Creditcard",
                  "extraData": { "remainingAmount": "5" }
                }""");

        TransactionResponse response = client().capture(
                CaptureRequest.of("tx", "ref", new BigDecimal("4.99"), "EUR"));

        assertThat(response.remainingAmount()).contains("5");
    }

    @Test
    void registerStoresAnInstrument() throws Exception {
        stubTransaction("register", FINISHED);

        client().register(RegisterRequest.builder("2019-09-02-0005")
                .description("This is a register")
                .customer(Customer.builder().firstName("John").lastName("Doe").build())
                .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("register"));
        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("2019-09-02-0005");
        assertThat(body.get("description").asText()).isEqualTo("This is a register");
        assertThat(body.has("amount")).as("a register never carries an amount").isFalse();
    }

    @Test
    void deregisterRemovesAStoredInstrument() throws Exception {
        stubTransaction("deregister", FINISHED);

        client().deregister(new DeregisterRequest("2019-09-02-0006", "bcdef23456bcdef23456",
                TokenType.ALL, null, null, null, "merchantRelevantData"));

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.get("tokenType").asText()).isEqualTo("ALL");
        assertThat(body.get("merchantMetaData").asText()).isEqualTo("merchantRelevantData");
    }

    @Test
    void payoutCreditsTheCustomer() throws Exception {
        stubTransaction("payout", FINISHED);

        client().payout(PayoutRequest.toReference("2019-09-02-0011", "bcdef23456bcdef23456",
                new BigDecimal("9.99"), "EUR"));

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("payout"));
        assertThat(mapper.readTree(request.getBodyAsString()).get("referenceUuid").asText())
                .isEqualTo("bcdef23456bcdef23456");
    }

    @Test
    @DisplayName("a recurring charge references the initial transaction's uuid")
    void recurringChargeUsesReferenceUuid() throws Exception {
        stubTransaction("debit", FINISHED);

        client().debit(PaymentRequest.builder("2019-09-02-0009", new BigDecimal("9.99"), "EUR")
                .referenceUuid("abcde12345abcde12345")
                .transactionIndicator(net.aetherealtech.bankart.model.TransactionIndicator.RECURRING)
                .build());

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("referenceUuid").asText()).isEqualTo("abcde12345abcde12345");
        assertThat(body.get("transactionIndicator").asText()).isEqualTo("RECURRING");
    }

    @Test
    @DisplayName("a payment.js token is sent as transactionToken")
    void tokenisedCardPayment() throws Exception {
        stubTransaction("debit", FINISHED);

        client().debit(PaymentRequest.builder("tx", new BigDecimal("9.99"), "EUR")
                .transactionToken("ix::tRaNsAcT1OnToK3N")
                .build());

        assertThat(mapper.readTree(onlyRequest().getBodyAsString()).get("transactionToken").asText())
                .isEqualTo("ix::tRaNsAcT1OnToK3N");
    }

    @Test
    @DisplayName("without a shared secret no signature headers are sent at all")
    void unsignedRequestsCarryNoSignature() {
        stubTransaction("debit", FINISHED);

        unsignedClient().debit(PaymentRequest.builder("tx", new BigDecimal("1.00"), "EUR").build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getHeader("X-Signature")).isNull();
        assertThat(request.getHeader("X-Date")).isNull();
        assertBasicAuth(request);
    }

    @Test
    @DisplayName("the response's returnData is read for whichever shape arrives")
    void parsesCardReturnData() {
        stubTransaction("debit", """
                {
                  "success": true,
                  "uuid": "abcde12345abcde12345",
                  "purchaseId": "20190927-abcde12345abcde12345",
                  "returnType": "FINISHED",
                  "paymentMethod": "Creditcard",
                  "returnData": {
                    "_TYPE": "cardData",
                    "type": "visa",
                    "lastFourDigits": "1111",
                    "binBrand": "VISA",
                    "fingerprint": "9s92FBBvMuw7nn8t"
                  }
                }""");

        TransactionResponse response = client().debit(
                PaymentRequest.builder("tx", new BigDecimal("1.00"), "EUR").build());

        assertThat(response.returnData().lastFourDigits()).isEqualTo("1111");
        assertThat(response.returnData().fingerprint()).isEqualTo("9s92FBBvMuw7nn8t");
    }
}
