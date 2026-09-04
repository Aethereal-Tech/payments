package net.aetherealtech.bankart.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.aetherealtech.bankart.Fixtures;
import net.aetherealtech.bankart.exception.BankartNotificationException;
import net.aetherealtech.bankart.model.TransactionType;

/** Every fixture here is copied from the API reference's own notification examples. */
class NotificationParserTest {

    private final NotificationParser parser = new NotificationParser();

    @Test
    @DisplayName("the acknowledgement is the literal string OK")
    void acknowledgementContract() {
        assertThat(Notification.ACKNOWLEDGEMENT).isEqualTo("OK");
        assertThat(NotificationParser.acknowledgement()).isEqualTo("OK");
    }

    @Test
    void parsesTheSuccessNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-success.json"));

        assertThat(notification.result()).isEqualTo(NotificationResult.OK);
        assertThat(notification.isSuccess()).isTrue();
        assertThat(notification.isError()).isFalse();
        assertThat(notification.isPending()).isFalse();
        assertThat(notification.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(notification.merchantTransactionId()).isEqualTo("2019-09-02-0007");
        assertThat(notification.purchaseId()).isEqualTo("20190927-abcde12345abcde12345");
        assertThat(notification.transactionType()).isEqualTo(TransactionType.DEBIT);
        assertThat(notification.paymentMethod()).isEqualTo("DirectDebit");
        assertThat(notification.amount()).isEqualByComparingTo("9.99");
        assertThat(notification.currency()).isEqualTo("EUR");
        assertThat(notification.customer().firstName()).isEqualTo("John");
        assertThat(notification.returnData().lastFourDigits()).isEqualTo("1111");
        assertThat(notification.returnData().binBrand()).isEqualTo("VISA");
        assertThat(notification.returnData().type()).isEqualTo("cardData");
        assertThat(notification.returnData().brand()).isEqualTo("visa");
    }

    @Test
    @DisplayName("emailVerified arrives as the string \"false\" in the docs' own example")
    void coercesStringBooleans() {
        Notification notification = parser.parse(Fixtures.load("notification-success.json"));

        assertThat(notification.customer().emailVerified()).isFalse();
    }

    @Test
    void parsesTheErrorNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-error.json"));

        assertThat(notification.result()).isEqualTo(NotificationResult.ERROR);
        assertThat(notification.isError()).isTrue();
        assertThat(notification.message()).isEqualTo("STOLEN_CARD");
        // The field table types this as a number; the example sends a string. Both must read.
        assertThat(notification.code()).isEqualTo(2016);
        assertThat(notification.adapterMessage()).isEqualTo("Transaction was rejected");
        assertThat(notification.adapterCode()).isEqualTo("1234");
    }

    @Test
    void parsesTheChargebackNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-chargeback.json"));

        assertThat(notification.isChargeback()).isTrue();
        assertThat(notification.transactionType()).isEqualTo(TransactionType.CHARGEBACK);
        // The chargeback example sends amounts as JSON numbers where other examples send strings.
        assertThat(notification.amount()).isEqualByComparingTo(new BigDecimal("9.99"));
        ChargebackData chargeback = notification.chargebackData();
        assertThat(chargeback.originalUuid()).isEqualTo("0r1gIN4luU1D");
        assertThat(chargeback.originalMerchantTransactionId()).isEqualTo("2019-09-02-0009");
        assertThat(chargeback.reason()).isEqualTo("Unauthorized payment");
        assertThat(chargeback.chargebackDateTime()).isEqualTo("2019-10-10T15:06:47Z");
        assertThat(chargeback.amount()).isEqualByComparingTo("9.99");
        assertThat(chargeback.currency()).isEqualTo("EUR");
    }

    @Test
    void parsesTheChargebackReversalNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-chargeback-reversal.json"));

        assertThat(notification.transactionType()).isEqualTo(TransactionType.CHARGEBACK_REVERSAL);
        assertThat(notification.isChargeback()).isFalse();
        ChargebackReversalData reversal = notification.chargebackReversalData();
        assertThat(reversal.chargebackUuid()).isEqualTo("Ch4rG3baCkUu1D");
        assertThat(reversal.originalUuid()).isEqualTo("0r1gIN4luU1D");
        assertThat(reversal.originalMerchantTransactionId()).isEqualTo("2019-09-02-0011");
        assertThat(reversal.reason()).isEqualTo("Chargeback reversed");
        assertThat(reversal.reversalDateTime()).isEqualTo("2019-10-15T16:22:12Z");
        assertThat(reversal.amount()).isEqualByComparingTo("9.99");
        assertThat(reversal.currency()).isEqualTo("EUR");
    }

    @Test
    void parsesTheAccountUpdaterNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-account-updater.json"));

        assertThat(notification.transactionType()).isEqualTo(TransactionType.REGISTER);
        assertThat(notification.extra("lastCardUpdateResult")).contains("updated");
        assertThat(notification.extra("lastCardUpdateDate")).contains("2022-08-19");
        assertThat(notification.extra("absent")).isEmpty();
        assertThat(notification.returnData().binBrand()).isEqualTo("MASTERCARD");
        assertThat(notification.returnData().expiryYear()).isEqualTo("2050");
    }

    @Test
    void parsesTheNetworkTokenNotification() {
        Notification notification = parser.parse(Fixtures.load("notification-network-token.json"));

        assertThat(notification.extra("networkTokenStatus")).contains("suspended");
        assertThat(notification.returnData().binLevel()).isEqualTo("CLASSIC");
    }

    @Test
    @DisplayName("a result value we have never seen reads as UNKNOWN rather than failing")
    void unknownEnumValuesDoNotBreakParsing() {
        Notification notification = parser.parse("{\"result\":\"SOMETHING_NEW\",\"transactionType\":\"NEWTYPE\"}");

        assertThat(notification.result()).isEqualTo(NotificationResult.UNKNOWN);
        assertThat(notification.transactionType()).isEqualTo(TransactionType.UNKNOWN);
    }

    @Test
    @DisplayName("a field the gateway adds later is ignored, not fatal")
    void unknownFieldsAreIgnored() {
        Notification notification = parser.parse("{\"result\":\"OK\",\"somethingAddedIn2027\":\"value\"}");

        assertThat(notification.isSuccess()).isTrue();
    }

    @Test
    void reportsPendingSeparately() {
        Notification notification = parser.parse("{\"result\":\"PENDING\"}");

        assertThat(notification.isPending()).isTrue();
        assertThat(notification.isSuccess()).isFalse();
    }

    @Test
    void exposesReconciliationRestatements() {
        Notification notification = parser.parse(
                "{\"result\":\"OK\",\"notificationSource\":\"reconciliation\",\"amount\":\"8.50\","
                        + "\"originalAmount\":\"9.99\",\"originalCurrency\":\"EUR\"}");

        assertThat(notification.restatedBy()).contains("reconciliation");
        assertThat(notification.originalAmount()).isEqualByComparingTo("9.99");
        assertThat(notification.originalCurrency()).isEqualTo("EUR");
        assertThat(notification.amount()).isEqualByComparingTo("8.50");
    }

    @Test
    void hasNoRestatementWhenTheGatewayReportsNone() {
        assertThat(parser.parse("{\"result\":\"OK\"}").restatedBy()).isEmpty();
        assertThat(parser.parse("{\"result\":\"OK\"}").extra("anything")).isEmpty();
    }

    @Test
    void rejectsAMalformedBody() {
        assertThatExceptionOfType(BankartNotificationException.class)
                .isThrownBy(() -> parser.parse("this is not json"))
                .withMessageContaining("Could not parse");
    }

    @Test
    void rejectsAnEmptyBody() {
        assertThatExceptionOfType(BankartNotificationException.class)
                .isThrownBy(() -> parser.parse(""))
                .withMessageContaining("empty");
    }

    @Test
    void rejectsANullBody() {
        assertThatNullPointerException().isThrownBy(() -> parser.parse((String) null));
        assertThatNullPointerException().isThrownBy(() -> parser.parse((byte[]) null));
    }

    @Test
    void acceptsAnExplicitlySuppliedMapper() {
        NotificationParser custom = new NotificationParser(net.aetherealtech.bankart.internal.Json.mapper());

        assertThat(custom.parse("{\"result\":\"OK\"}").isSuccess()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> new NotificationParser(null));
    }

}
