package net.aetherealtech.payments.bankart.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.internal.Json;

/** All four {@code _TYPE} variants, so a non-card instrument stops reading as nothing. */
class ReturnDataTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    void cardDataKeepsItsShape() throws Exception {
        ReturnData data = read("""
                {"_TYPE":"cardData","type":"visa","cardHolder":"John Doe","expiryMonth":"12",
                 "expiryYear":"2022","binDigits":"41111111","firstSixDigits":"411111",
                 "lastFourDigits":"1111","fingerprint":"9s92","threeDSecure":"OFF","binBrand":"VISA",
                 "binBank":"CHASE","binType":"credit","binLevel":"CLASSIC","binCountry":"US"}""");

        assertThat(data).isInstanceOf(CardData.class);
        CardData card = (CardData) data;
        assertThat(card.type()).isEqualTo("cardData");
        assertThat(card.brand()).isEqualTo("visa");
        assertThat(card.lastFourDigits()).isEqualTo("1111");
        assertThat(card.binCountry()).isEqualTo("US");
        assertThat(card.binType()).isEqualTo("credit");
        assertThat(card.binLevel()).isEqualTo("CLASSIC");
        assertThat(card.binBank()).isEqualTo("CHASE");
        assertThat(card.cardHolder()).isEqualTo("John Doe");
        assertThat(card.expiryMonth()).isEqualTo("12");
        assertThat(card.expiryYear()).isEqualTo("2022");
        assertThat(card.binDigits()).isEqualTo("41111111");
        assertThat(card.firstSixDigits()).isEqualTo("411111");
        assertThat(card.fingerprint()).isEqualTo("9s92");
        assertThat(card.threeDSecure()).isEqualTo("OFF");
        assertThat(card.binBrand()).isEqualTo("VISA");
    }

    @Test
    void ibanDataParsesRatherThanReadingAsNull() throws Exception {
        ReturnData data = read("""
                {"_TYPE":"ibanData","accountOwner":"John Doe","iban":"AT123456789012345678",
                 "bic":"ABCDATWW","mandateId":"1234","mandateDate":"2019-09-29",
                 "bankName":"Example Bank","bankBranchName":"Vienna","country":"AT"}""");

        assertThat(data).isInstanceOf(ReturnIbanData.class);
        ReturnIbanData iban = (ReturnIbanData) data;
        assertThat(iban.type()).isEqualTo("ibanData");
        assertThat(iban.accountOwner()).isEqualTo("John Doe");
        assertThat(iban.iban()).isEqualTo("AT123456789012345678");
        assertThat(iban.bic()).isEqualTo("ABCDATWW");
        assertThat(iban.mandateId()).isEqualTo("1234");
        assertThat(iban.mandateDate()).isEqualTo("2019-09-29");
        assertThat(iban.bankName()).isEqualTo("Example Bank");
        assertThat(iban.bankBranchName()).isEqualTo("Vienna");
        assertThat(iban.country()).isEqualTo("AT");
    }

    @Test
    void phoneData() throws Exception {
        ReturnData data = read("{\"_TYPE\":\"phoneData\",\"phoneNumber\":\"+38970123456\","
                + "\"country\":\"MK\",\"operator\":\"A1\"}");

        assertThat(data).isInstanceOf(ReturnPhoneData.class);
        ReturnPhoneData phone = (ReturnPhoneData) data;
        assertThat(phone.type()).isEqualTo("phoneData");
        assertThat(phone.phoneNumber()).isEqualTo("+38970123456");
        assertThat(phone.country()).isEqualTo("MK");
        assertThat(phone.operator()).isEqualTo("A1");
    }

    @Test
    void walletData() throws Exception {
        ReturnData data = read("{\"_TYPE\":\"walletData\",\"walletReferenceId\":\"WAL-0001\","
                + "\"walletOwner\":\"John Doe\",\"walletType\":\"PayPal\"}");

        assertThat(data).isInstanceOf(ReturnWalletData.class);
        ReturnWalletData wallet = (ReturnWalletData) data;
        assertThat(wallet.type()).isEqualTo("walletData");
        assertThat(wallet.walletReferenceId()).isEqualTo("WAL-0001");
        assertThat(wallet.walletOwner()).isEqualTo("John Doe");
        assertThat(wallet.walletType()).isEqualTo("PayPal");
    }

    @Test
    @DisplayName("the status API's nested card shape still reads as a card")
    void nestedCardShapes() throws Exception {
        assertThat(read("{\"creditcardData\":{\"lastFourDigits\":\"4321\"}}"))
                .isInstanceOfSatisfying(CardData.class, card -> {
                    assertThat(card.lastFourDigits()).isEqualTo("4321");
                    assertThat(card.type()).isEqualTo("creditcardData");
                });
        assertThat(read("{\"cardData\":{\"lastFourDigits\":\"9999\",\"_TYPE\":\"cardData\"}}"))
                .isInstanceOfSatisfying(CardData.class, card -> assertThat(card.type()).isEqualTo("cardData"));
    }

    @Test
    @DisplayName("a flat card payload with no _TYPE is still a card")
    void flatCardWithoutDiscriminator() throws Exception {
        assertThat(read("{\"lastFourDigits\":\"1111\"}")).isInstanceOf(CardData.class);
        assertThat(read("{\"cardHolder\":\"John Doe\"}")).isInstanceOf(CardData.class);
    }

    @Test
    void cardDataAccessorsAreReachableThroughEveryCarrier() throws Exception {
        String card = "{\"_TYPE\":\"cardData\",\"lastFourDigits\":\"1111\"}";
        String iban = "{\"_TYPE\":\"ibanData\",\"iban\":\"AT12\"}";

        assertThat(mapper.readValue(wrapTransaction(card), TransactionResponse.class).cardData())
                .hasValueSatisfying(c -> assertThat(c.lastFourDigits()).isEqualTo("1111"));
        assertThat(mapper.readValue(wrapTransaction(iban), TransactionResponse.class).cardData()).isEmpty();
        assertThat(mapper.readValue(wrapStatus(card), StatusResponse.class).cardData())
                .hasValueSatisfying(c -> assertThat(c.lastFourDigits()).isEqualTo("1111"));
        assertThat(mapper.readValue(wrapStatus(iban), StatusResponse.class).cardData()).isEmpty();
    }

    private static String wrapTransaction(String returnData) {
        return "{\"success\":true,\"returnType\":\"FINISHED\",\"returnData\":" + returnData + "}";
    }

    private static String wrapStatus(String returnData) {
        return "{\"success\":true,\"transactionStatus\":\"FINISHED\",\"returnData\":" + returnData + "}";
    }

    private ReturnData read(String returnData) throws Exception {
        return mapper.readValue(wrapTransaction(returnData), TransactionResponse.class).returnData();
    }
}
