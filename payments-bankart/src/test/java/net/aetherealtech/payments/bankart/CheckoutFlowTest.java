package net.aetherealtech.payments.bankart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.bankart.exception.BankartException;
import net.aetherealtech.payments.bankart.exception.BankartSignatureException;
import net.aetherealtech.payments.bankart.exception.BankartTransactionException;
import net.aetherealtech.payments.bankart.model.Customer;
import net.aetherealtech.payments.bankart.model.PaymentRequest;
import net.aetherealtech.payments.bankart.model.RedirectResult;
import net.aetherealtech.payments.bankart.model.RedirectType;
import net.aetherealtech.payments.bankart.model.RegisterRequest;
import net.aetherealtech.payments.bankart.notification.Notification;
import net.aetherealtech.payments.bankart.notification.NotificationVerifier;
import net.aetherealtech.payments.bankart.signing.HmacSigner;
import net.aetherealtech.payments.bankart.signing.SignedRequest;

/**
 * The whole hosted-checkout round trip, end to end: start the payment, take the redirect, then
 * receive, verify and acknowledge the notification that actually decides it.
 *
 * <p>Written as one narrative because the pieces only make sense together — in particular that the
 * order is settled by the callback and not by the synchronous response, and that the same
 * notification will arrive again if the acknowledgement is not exactly HTTP 200 "OK".
 */
class CheckoutFlowTest extends GatewayTestBase {

    private static final String CALLBACK_URI = "/payments/bankart/callback";

    private static final String REDIRECT_RESPONSE = """
            {
              "success": true,
              "uuid": "abcde12345abcde12345",
              "purchaseId": "20190927-abcde12345abcde12345",
              "returnType": "REDIRECT",
              "redirectType": "iframe",
              "redirectUrl": "https://gateway.bankart.si/payment/form/abcde12345",
              "paymentMethod": "Creditcard"
            }""";

    /** Stands in for the merchant's order table. */
    private final Map<String, String> orders = new ConcurrentHashMap<>();

    @Test
    @DisplayName("start a checkout, then settle it on the notification")
    void hostedCheckoutRoundTrip() {
        stubTransaction("debit", REDIRECT_RESPONSE);

        RedirectResult redirect = client().startCheckout(
                PaymentRequest.builder("2019-09-02-0007", new BigDecimal("9.99"), "EUR")
                        .redirectUrls("https://shop.example/success", "https://shop.example/cancel",
                                "https://shop.example/error", "https://shop.example" + CALLBACK_URI)
                        .customer(Customer.builder().firstName("John").lastName("Doe").build())
                        .description("Example Product")
                        .build());

        assertThat(redirect.redirectUrl()).isEqualTo("https://gateway.bankart.si/payment/form/abcde12345");
        assertThat(redirect.redirectType()).isEqualTo(RedirectType.IFRAME);
        assertThat(redirect.isIframe()).isTrue();
        assertThat(redirect.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(redirect.purchaseId()).isEqualTo("20190927-abcde12345abcde12345");
        assertSignature(onlyRequest(), "POST", transactionPath("debit"));

        // What a merchant must do before sending the customer anywhere: the notification can arrive
        // before the browser comes back, and it will name this uuid.
        orders.put(redirect.uuid(), "AWAITING_PAYMENT");

        String body = notificationFor(redirect.uuid(), "2019-09-02-0007");
        String acknowledgement = receiveCallback(body, sign(body));

        assertThat(acknowledgement).isEqualTo("OK");
        assertThat(orders).containsEntry(redirect.uuid(), "PAID");
    }

    @Test
    @DisplayName("a repeated notification is safe, which it has to be — the gateway retries")
    void notificationsAreIdempotent() {
        String body = notificationFor("uuid-1", "tx-1");
        orders.put("uuid-1", "AWAITING_PAYMENT");

        receiveCallback(body, sign(body));
        receiveCallback(body, sign(body));

        assertThat(orders).containsEntry("uuid-1", "PAID");
        assertThat(orders).hasSize(1);
    }

    @Test
    @DisplayName("a failed transaction may later succeed, and the later word wins")
    void aFailureCanBeFollowedBySuccess() {
        orders.put("uuid-2", "AWAITING_PAYMENT");
        String failure = """
                {"result":"ERROR","uuid":"uuid-2","merchantTransactionId":"tx-2","code":"2003",
                 "message":"Transaction declined"}""";
        String success = notificationFor("uuid-2", "tx-2");

        receiveCallback(failure, sign(failure));
        assertThat(orders).containsEntry("uuid-2", "FAILED");

        receiveCallback(success, sign(success));
        assertThat(orders).containsEntry("uuid-2", "PAID");
    }

    @Test
    @DisplayName("an unsigned or forged callback never reaches the order")
    void aForgedCallbackIsRefused() {
        orders.put("uuid-3", "AWAITING_PAYMENT");
        String body = notificationFor("uuid-3", "tx-3");
        String forged = new HmacSigner("attacker-secret")
                .sign(SignedRequest.post(body, CONTENT_TYPE, HmacSigner.formatDate(java.time.Instant.now()),
                        CALLBACK_URI));

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> receiveCallback(body, forged));

        assertThat(orders).containsEntry("uuid-3", "AWAITING_PAYMENT");
    }

    @Test
    void aDeclinedCheckoutThrowsWithTheGatewaySReason() {
        stubTransaction("debit", """
                {
                  "success": false,
                  "uuid": "abcde12345abcde12345",
                  "returnType": "ERROR",
                  "errors": [{"errorMessage": "Stolen card", "errorCode": 2016,
                              "adapterMessage": "declined", "adapterCode": "05"}]
                }""");

        assertThatExceptionOfType(BankartTransactionException.class)
                .isThrownBy(() -> client().startCheckout(
                        PaymentRequest.builder("tx", new BigDecimal("9.99"), "EUR").build()))
                .satisfies(e -> {
                    assertThat(e.firstError().orElseThrow().errorCode()).isEqualTo(2016);
                    assertThat(e.uuid()).isEqualTo("abcde12345abcde12345");
                });
    }

    @Test
    @DisplayName("a connector that answers FINISHED instead of REDIRECT is a configuration problem, and says so")
    void aNonRedirectResponseIsRejected() {
        stubTransaction("debit", """
                {"success": true, "uuid": "u", "returnType": "FINISHED", "paymentMethod": "Creditcard"}""");

        assertThatExceptionOfType(BankartException.class)
                .isThrownBy(() -> client().startCheckout(
                        PaymentRequest.builder("tx", new BigDecimal("9.99"), "EUR").build()))
                .withMessageContaining("Expected a REDIRECT")
                .withMessageContaining("FINISHED");
    }

    @Test
    void aRedirectWithoutAUrlIsRejected() {
        stubTransaction("debit", """
                {"success": true, "uuid": "u", "returnType": "REDIRECT", "paymentMethod": "Creditcard"}""");

        assertThatExceptionOfType(BankartException.class)
                .isThrownBy(() -> client().startCheckout(
                        PaymentRequest.builder("tx", new BigDecimal("9.99"), "EUR").build()))
                .withMessageContaining("Expected a REDIRECT");
    }

    @Test
    @DisplayName("registering a card through the hosted page redirects the same way")
    void hostedRegistrationRoundTrip() {
        stubTransaction("register", REDIRECT_RESPONSE);

        RedirectResult redirect = client().startRegistration(RegisterRequest.builder("2019-09-02-0005")
                .redirectUrls("https://shop.example/success", "https://shop.example/cancel",
                        "https://shop.example/error", "https://shop.example" + CALLBACK_URI)
                .build());

        assertThat(redirect.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(redirect.redirectUrl()).contains("gateway.bankart.si");
    }

    @Test
    void aDeclinedRegistrationThrows() {
        stubTransaction("register", """
                {"success": false, "uuid": "u", "returnType": "ERROR", "errors": []}""");

        assertThatExceptionOfType(BankartTransactionException.class)
                .isThrownBy(() -> client().startRegistration(RegisterRequest.builder("tx").build()));
    }

    /**
     * What a merchant's callback endpoint does: verify, act, and only then answer "OK". Returning the
     * acknowledgement before the state is durable would turn a crash into a lost payment, since the
     * gateway takes 200/OK as the end of its retry schedule.
     */
    private String receiveCallback(String body, String signature) {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE,
                HmacSigner.formatDate(java.time.Instant.now()), CALLBACK_URI);
        Notification notification = new NotificationVerifier(SHARED_SECRET).verifyAndParse(request, signature);

        orders.put(notification.uuid(), notification.isSuccess() ? "PAID" : "FAILED");

        return Notification.ACKNOWLEDGEMENT;
    }

    private String sign(String body) {
        return new HmacSigner(SHARED_SECRET).sign(SignedRequest.post(body, CONTENT_TYPE,
                HmacSigner.formatDate(java.time.Instant.now()), CALLBACK_URI));
    }

    private static String notificationFor(String uuid, String merchantTransactionId) {
        return """
                {
                  "result": "OK",
                  "uuid": "%s",
                  "merchantTransactionId": "%s",
                  "purchaseId": "20190927-abcde12345abcde12345",
                  "transactionType": "DEBIT",
                  "paymentMethod": "Creditcard",
                  "amount": "9.99",
                  "currency": "EUR"
                }""".formatted(uuid, merchantTransactionId);
    }
}
