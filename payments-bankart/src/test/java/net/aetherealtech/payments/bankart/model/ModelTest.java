package net.aetherealtech.payments.bankart.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.internal.Json;

/** The request records reject at construction what the gateway would otherwise reject at a distance. */
class ModelTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    @DisplayName("amounts serialise as strings with a dot, never as JSON numbers")
    void amountsAreStrings() throws Exception {
        PaymentRequest request = PaymentRequest.builder("tx-1", new BigDecimal("9.99"), "EUR").build();

        assertThat(mapper.writeValueAsString(request))
                .contains("\"amount\":\"9.99\"")
                .doesNotContain("\"amount\":9.99");
    }

    @Test
    void amountsNeverGoOutInScientificNotation() throws Exception {
        PaymentRequest request = PaymentRequest.builder("tx-1", new BigDecimal("9.99E+2"), "EUR").build();

        assertThat(mapper.writeValueAsString(request)).contains("\"amount\":\"999\"");
    }

    @Test
    @DisplayName("more than three decimals is refused where the caller can still see why")
    void rejectsOverPreciseAmounts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentRequest.builder("tx-1", new BigDecimal("9.9999"), "EUR").build())
                .withMessageContaining("at most 3 decimals");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentRequest.builder("tx-1", new BigDecimal("1.00"), "EUR")
                        .surchargeAmount(new BigDecimal("0.12345")).build());
    }

    @Test
    void acceptsExactlyThreeDecimals() {
        assertThat(PaymentRequest.builder("tx-1", new BigDecimal("9.999"), "EUR").build().amount())
                .isEqualByComparingTo("9.999");
    }

    @Test
    void rejectsMissingMandatoryFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentRequest.builder("", BigDecimal.ONE, "EUR").build())
                .withMessageContaining("merchantTransactionId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentRequest.builder("tx-1", BigDecimal.ONE, " ").build())
                .withMessageContaining("currency");
        assertThatNullPointerException()
                .isThrownBy(() -> PaymentRequest.builder("tx-1", null, "EUR").build());
    }

    @Test
    @DisplayName("null optional fields are omitted from the body entirely")
    void omitsNulls() throws Exception {
        String json = mapper.writeValueAsString(
                PaymentRequest.builder("tx-1", new BigDecimal("1.00"), "EUR").build());

        assertThat(json).doesNotContain("null").doesNotContain("customer").doesNotContain("captureInMinutes");
    }

    @Test
    void buildsAFullPaymentRequest() throws Exception {
        Customer customer = Customer.builder()
                .identification("c0001").firstName("John").lastName("Doe").birthDate("1990-10-10")
                .gender("M").billingAddress1("Maple Street 1").billingAddress2("Syrup Street 2")
                .billingCity("Victoria").billingPostcode("V8W").billingState("British Columbia")
                .billingCountry("CA").billingPhone("1234567890").shippingFirstName("John")
                .shippingLastName("Doe").shippingCompany("Big Company Inc.").shippingAddress1("Yellow alley 3")
                .shippingAddress2("Yellow alley 4").shippingCity("Victoria").shippingPostcode("V8W")
                .shippingState("British Columbia").shippingCountry("CA").shippingPhone("1234567890")
                .company("John's Maple Syrup").email("john.doe@example.com").emailVerified(false)
                .ipAddress("127.0.0.1").nationalId("123123").extraData(Map.of("someCustomerDataKey", "value"))
                .paymentData(PaymentData.ofIban(new IbanData("AT123456789012345678", "ABC", "1234", "2019-09-29")))
                .build();

        PaymentRequest request = PaymentRequest.builder("2019-09-02-0001", new BigDecimal("9.99"), "EUR")
                .surchargeAmount(new BigDecimal("0.9"))
                .additionalId1("x0001").additionalId2("y0001")
                .extraData(Map.of("someKey", "someValue"))
                .merchantMetaData("merchantRelevantData")
                .referenceUuid("bcdef23456bcdef23456")
                .redirectUrls("https://example.com/success", "https://example.com/cancel",
                        "https://example.com/error", "https://example.com/callback")
                .transactionToken("ix::tRaNsAcT1OnToK3N")
                .description("Example Product")
                .withRegister(true)
                .transactionIndicator(TransactionIndicator.INITIAL)
                .customer(customer)
                .threeDSecureData(ThreeDSecureData.of(ThreeDSecureMode.MANDATORY))
                .language("en")
                .includeTracing(true)
                .build();

        String json = mapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"merchantTransactionId\":\"2019-09-02-0001\"")
                .contains("\"surchargeAmount\":\"0.9\"")
                .contains("\"successUrl\":\"https://example.com/success\"")
                .contains("\"cancelUrl\":\"https://example.com/cancel\"")
                .contains("\"errorUrl\":\"https://example.com/error\"")
                .contains("\"callbackUrl\":\"https://example.com/callback\"")
                .contains("\"withRegister\":true")
                .contains("\"transactionIndicator\":\"INITIAL\"")
                .contains("\"3dsecure\":\"MANDATORY\"")
                .contains("\"iban\":\"AT123456789012345678\"")
                .contains("\"includeTracing\":true")
                .contains("\"additionalId1\":\"x0001\"")
                .contains("\"description\":\"Example Product\"");
        assertThat(request.customer().email()).isEqualTo("john.doe@example.com");
        assertThat(request.customer().emailVerified()).isFalse();
    }

    @Test
    @DisplayName("the merchant-initiated indicator keeps its hyphenated wire spelling")
    void hyphenatedEnumValues() throws Exception {
        PaymentRequest request = PaymentRequest.builder("tx-1", BigDecimal.ONE, "EUR")
                .transactionIndicator(TransactionIndicator.CARDONFILE_MERCHANT_INITIATED)
                .build();

        assertThat(mapper.writeValueAsString(request))
                .contains("\"transactionIndicator\":\"CARDONFILE-MERCHANT-INITIATED\"");
    }

    @Test
    void capturesVoidsRefundsAndDeregistersValidateTheirReference() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CaptureRequest.of("tx", "", BigDecimal.ONE, "EUR"))
                .withMessageContaining("referenceUuid");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VoidRequest.of("tx", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefundRequest.of("", "ref", BigDecimal.ONE, "EUR"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DeregisterRequest.of("tx", " "));
        assertThatNullPointerException()
                .isThrownBy(() -> CaptureRequest.of("tx", "ref", null, "EUR"));
        assertThatNullPointerException()
                .isThrownBy(() -> RefundRequest.of("tx", "ref", null, "EUR"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CaptureRequest.of("tx", "ref", BigDecimal.ONE, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefundRequest.of("tx", "ref", BigDecimal.ONE, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VoidRequest("tx", "ref", new BigDecimal("1.00001"), "EUR", null, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CaptureRequest("tx", "ref", new BigDecimal("1.00001"), "EUR", null, null, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RefundRequest("tx", "ref", new BigDecimal("1.00001"), "EUR", null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("a payout with no destination is refused rather than round-tripped")
    void payoutNeedsADestination() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PayoutRequest("tx", BigDecimal.ONE, "EUR", null, null,
                        null, null, null, null, null, null, null, null))
                .withMessageContaining("referenceUuid or transactionToken");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PayoutRequest("tx", BigDecimal.ONE, "EUR", " ", "  ",
                        null, null, null, null, null, null, null, null));
        assertThat(PayoutRequest.toReference("tx", "ref", BigDecimal.ONE, "EUR").referenceUuid()).isEqualTo("ref");
        assertThat(new PayoutRequest("tx", BigDecimal.ONE, "EUR", null, "token",
                null, null, null, null, null, null, null, null).transactionToken()).isEqualTo("token");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PayoutRequest("", BigDecimal.ONE, "EUR", "ref", null,
                        null, null, null, null, null, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PayoutRequest("tx", BigDecimal.ONE, "", "ref", null,
                        null, null, null, null, null, null, null, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new PayoutRequest("tx", null, "EUR", "ref", null,
                        null, null, null, null, null, null, null, null));
    }

    @Test
    void buildsARegisterRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder("2019-09-02-0005")
                .additionalId1("x0001").additionalId2("y0001")
                .extraData(Map.of("someKey", "someValue"))
                .merchantMetaData("merchantRelevantData")
                .redirectUrls("https://example.com/success", "https://example.com/cancel",
                        "https://example.com/error", "https://example.com/callback")
                .transactionToken("ix::tok")
                .description("This is a register")
                .customer(Customer.builder().firstName("John").build())
                .threeDSecureData(ThreeDSecureData.of(ThreeDSecureMode.OPTIONAL))
                .language("en")
                .build();

        assertThat(mapper.writeValueAsString(request))
                .contains("\"merchantTransactionId\":\"2019-09-02-0005\"")
                .contains("\"description\":\"This is a register\"")
                .contains("\"3dsecure\":\"OPTIONAL\"")
                .contains("\"language\":\"en\"");
        assertThatIllegalArgumentException().isThrownBy(() -> RegisterRequest.builder("").build());
    }

    @Test
    void serialisesTheDeregisterTokenType() throws Exception {
        DeregisterRequest request = new DeregisterRequest("tx", "ref", TokenType.PAN, null, null, null, null);

        assertThat(mapper.writeValueAsString(request)).contains("\"tokenType\":\"PAN\"");
        assertThat(TokenType.ALL.wireValue()).isEqualTo("ALL");
        assertThat(TokenType.NT.wireValue()).isEqualTo("NT");
    }

    @Test
    @DisplayName("unrecognised enum values degrade to UNKNOWN, as the forward-compatibility rules require")
    void enumsToleratePreviouslyUnseenValues() {
        assertThat(ReturnType.fromWire("FINISHED")).isEqualTo(ReturnType.FINISHED);
        assertThat(ReturnType.fromWire("finished")).isEqualTo(ReturnType.FINISHED);
        assertThat(ReturnType.fromWire("SOMETHING_NEW")).isEqualTo(ReturnType.UNKNOWN);
        assertThat(ReturnType.fromWire(null)).isEqualTo(ReturnType.UNKNOWN);
        assertThat(ReturnType.HTML.wireValue()).isEqualTo("HTML");

        assertThat(RedirectType.fromWire("iframe")).isEqualTo(RedirectType.IFRAME);
        assertThat(RedirectType.fromWire("3ds")).isEqualTo(RedirectType.THREE_DS);
        assertThat(RedirectType.fromWire("fullpage")).isEqualTo(RedirectType.FULLPAGE);
        assertThat(RedirectType.fromWire("hologram")).isEqualTo(RedirectType.UNKNOWN);
        assertThat(RedirectType.fromWire(null)).isEqualTo(RedirectType.UNKNOWN);
        assertThat(RedirectType.THREE_DS.wireValue()).isEqualTo("3ds");

        assertThat(TransactionType.fromWire("CHARGEBACK-REVERSAL")).isEqualTo(TransactionType.CHARGEBACK_REVERSAL);
        assertThat(TransactionType.fromWire("debit")).isEqualTo(TransactionType.DEBIT);
        assertThat(TransactionType.fromWire("brand-new")).isEqualTo(TransactionType.UNKNOWN);
        assertThat(TransactionType.fromWire(null)).isEqualTo(TransactionType.UNKNOWN);
        assertThat(TransactionType.PAYOUT.wireValue()).isEqualTo("PAYOUT");

        assertThat(TransactionIndicator.fromWire("cardonfile")).isEqualTo(TransactionIndicator.CARDONFILE);
        assertThat(TransactionIndicator.fromWire("nope")).isEqualTo(TransactionIndicator.UNKNOWN);
        assertThat(TransactionIndicator.fromWire(null)).isEqualTo(TransactionIndicator.UNKNOWN);
        assertThat(TransactionIndicator.MOTO.wireValue()).isEqualTo("MOTO");

        assertThat(ThreeDSecureMode.fromWire("mandatory")).isEqualTo(ThreeDSecureMode.MANDATORY);
        assertThat(ThreeDSecureMode.fromWire("maybe")).isEqualTo(ThreeDSecureMode.UNKNOWN);
        assertThat(ThreeDSecureMode.fromWire(null)).isEqualTo(ThreeDSecureMode.UNKNOWN);
        assertThat(ThreeDSecureMode.OFF.wireValue()).isEqualTo("OFF");
    }

    @Test
    @DisplayName("returnData parses whether the card fields are flat or nested under a discriminator")
    void bothReturnDataShapes() throws Exception {
        String flat = "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":"
                + "{\"_TYPE\":\"cardData\",\"lastFourDigits\":\"1111\",\"binBrand\":\"VISA\"}}";
        String nested = "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":"
                + "{\"creditcardData\":{\"lastFourDigits\":\"4321\",\"binBrand\":\"VISA\"}}}";

        assertThat(mapper.readValue(flat, TransactionResponse.class).cardData().orElseThrow().lastFourDigits()).isEqualTo("1111");
        TransactionResponse nestedResponse = mapper.readValue(nested, TransactionResponse.class);
        assertThat(nestedResponse.cardData().orElseThrow().lastFourDigits()).isEqualTo("4321");
        assertThat(nestedResponse.cardData().orElseThrow().type()).isEqualTo("creditcardData");
    }

    @Test
    @DisplayName("a variant the discriminator does not name reads as null, never as an empty card")
    void unmappedReturnDataReadsAsNull() throws Exception {
        String unmapped = "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":"
                + "{\"_TYPE\":\"cryptoData\",\"address\":\"bc1q\"}}";
        String absent = "{\"success\":true,\"returnType\":\"FINISHED\"}";
        String notAnObject = "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":\"unexpected\"}";
        String noDiscriminatorAndNoCardFields =
                "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":{\"somethingElse\":\"x\"}}";

        assertThat(mapper.readValue(unmapped, TransactionResponse.class).returnData()).isNull();
        assertThat(mapper.readValue(absent, TransactionResponse.class).returnData()).isNull();
        assertThat(mapper.readValue(notAnObject, TransactionResponse.class).returnData()).isNull();
        assertThat(mapper.readValue(noDiscriminatorAndNoCardFields, TransactionResponse.class).returnData()).isNull();
    }

    @Test
    void transactionResponseExposesTheOutcome() {
        TransactionResponse redirect = new TransactionResponse(true, "uuid", "pid", ReturnType.REDIRECT,
                RedirectType.IFRAME, "https://pay.example", null, null, null, "Creditcard", null, null,
                Map.of("remainingAmount", "5"), null);

        assertThat(redirect.isRedirect()).isTrue();
        assertThat(redirect.isError()).isFalse();
        assertThat(redirect.remainingAmount()).contains("5");
        assertThat(redirect.errors()).isEmpty();

        TransactionResponse failed = new TransactionResponse(false, "uuid", "pid", ReturnType.ERROR,
                null, null, null, null, null, null, null, null, null,
                List.of(new TransactionError("Request failed", 1000, "Invalid parameters given", "1234")));

        assertThat(failed.isError()).isTrue();
        assertThat(failed.isRedirect()).isFalse();
        assertThat(failed.remainingAmount()).isEmpty();
        assertThat(failed.errors()).hasSize(1);

        TransactionResponse pendingButUnsuccessful = new TransactionResponse(false, "uuid", "pid",
                ReturnType.PENDING, null, null, null, null, null, null, null, null, null, null);
        assertThat(pendingButUnsuccessful.isError()).isTrue();
    }

    @Test
    void redirectResultCarriesWhatTheCallerMustPersist() {
        TransactionResponse response = new TransactionResponse(true, "uuid-1", "purchase-1", ReturnType.REDIRECT,
                RedirectType.IFRAME, "https://pay.example/x", null, null, null, "Creditcard", null, null, null, null);

        RedirectResult result = RedirectResult.from(response);

        assertThat(result.redirectUrl()).isEqualTo("https://pay.example/x");
        assertThat(result.uuid()).isEqualTo("uuid-1");
        assertThat(result.purchaseId()).isEqualTo("purchase-1");
        assertThat(result.paymentMethod()).isEqualTo("Creditcard");
        assertThat(result.isIframe()).isTrue();
        assertThat(new RedirectResult("u", RedirectType.FULLPAGE, null, null, null).isIframe()).isFalse();
        assertThatNullPointerException()
                .isThrownBy(() -> new RedirectResult(null, RedirectType.IFRAME, null, null, null));
    }

    @Test
    void amountValidationPassesNullThrough() {
        assertThat(AmountSerializer.requireValid(null, "amount")).isNull();
        assertThat(AmountSerializer.MAX_SCALE).isEqualTo(3);
    }

    @Test
    void threeDSecureDataCarriesTheOptionalFrictionlessFields() throws Exception {
        ThreeDSecureData data = new ThreeDSecureData(ThreeDSecureMode.MANDATORY, "CB", "02", "01", "03",
                "2020-01-01 12:00");

        assertThat(mapper.writeValueAsString(data))
                .contains("\"3dsecure\":\"MANDATORY\"")
                .contains("\"schemeId\":\"CB\"")
                .contains("\"channel\":\"02\"")
                .contains("\"authenticationIndicator\":\"01\"")
                .contains("\"cardholderAuthenticationMethod\":\"03\"")
                .contains("\"cardholderAuthenticationDateTime\":\"2020-01-01 12:00\"");
    }
}
