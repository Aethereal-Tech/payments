package net.aetherealtech.bankart.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.aetherealtech.bankart.Fixtures;
import net.aetherealtech.bankart.exception.BankartSignatureException;
import net.aetherealtech.bankart.signing.BodyDigest;
import net.aetherealtech.bankart.signing.HmacSigner;
import net.aetherealtech.bankart.signing.SignedRequest;

/**
 * Verification is the security boundary of the callback: without it, anyone who learns a callback
 * URL can declare a payment successful. These tests hold that boundary shut on every axis the
 * documentation names.
 */
class NotificationVerifierTest {

    private static final String SECRET = "my-shared-secret";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String CALLBACK_URI = "/payments/bankart/callback";
    private static final Instant NOW = Instant.parse("2020-07-21T13:15:03Z");
    private static final String DATE = HmacSigner.formatDate(NOW);

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final NotificationVerifier verifier =
            new NotificationVerifier(SECRET, BodyDigest.SHA512, Duration.ofSeconds(60), fixedClock);

    private final String body = Fixtures.load("notification-success.json");

    @Test
    void acceptsAGenuineNotification() {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);

        assertThatCode(() -> verifier.verify(request, signatureFor(request))).doesNotThrowAnyException();
    }

    @Test
    void parsesOnlyAfterVerifying() {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);

        Notification notification = verifier.verifyAndParse(request, signatureFor(request));

        assertThat(notification.uuid()).isEqualTo("abcde12345abcde12345");
        assertThat(notification.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("a body edited after signing is rejected")
    void rejectsATamperedBody() {
        SignedRequest genuine = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);
        String signature = signatureFor(genuine);
        SignedRequest tampered = SignedRequest.post(
                body.replace("\"9.99\"", "\"0.01\""), CONTENT_TYPE, DATE, CALLBACK_URI);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(tampered, signature))
                .withMessageContaining("did not match");
    }

    @Test
    @DisplayName("a payload signed with someone else's secret is rejected")
    void rejectsAForeignSignature() {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);
        String forged = new HmacSigner("attacker-secret").sign(request);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, forged));
    }

    @Test
    void rejectsAMissingSignature() {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, null))
                .withMessageContaining(NotificationVerifier.SIGNATURE_HEADER);
        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, "   "));
    }

    @Test
    @DisplayName("a correctly signed but stale notification is rejected, closing the replay window")
    void rejectsAStaleDate() {
        String staleDate = HmacSigner.formatDate(NOW.minus(Duration.ofMinutes(5)));
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, staleDate, CALLBACK_URI);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, signatureFor(request)))
                .withMessageContaining("beyond the 60s window");
    }

    @Test
    void rejectsADateFromTheFuture() {
        String future = HmacSigner.formatDate(NOW.plus(Duration.ofMinutes(5)));
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, future, CALLBACK_URI);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, signatureFor(request)));
    }

    @Test
    void acceptsADateInsideTheWindow() {
        String slightlyOld = HmacSigner.formatDate(NOW.minusSeconds(45));
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, slightlyOld, CALLBACK_URI);

        assertThatCode(() -> verifier.verify(request, signatureFor(request))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the docs print UTC where RFC 7231 says GMT, so both spellings parse")
    void acceptsBothDateSpellings() {
        SignedRequest utc = SignedRequest.post(body, CONTENT_TYPE, "Tue, 21 Jul 2020 13:15:03 UTC", CALLBACK_URI);

        assertThatCode(() -> verifier.verify(utc, signatureFor(utc))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnparseableDate() {
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, "yesterday afternoon", CALLBACK_URI);

        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, signatureFor(request)))
                .withMessageContaining("unparseable");
    }

    @Test
    @DisplayName("a callback URL carrying query parameters signs path-and-query")
    void signsTheQueryStringToo() {
        String withQuery = CALLBACK_URI + "?orderId=42";
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, withQuery);
        String signature = signatureFor(request);

        assertThatCode(() -> verifier.verify(request, signature)).doesNotThrowAnyException();
        SignedRequest pathOnly = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);
        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(pathOnly, signature));
    }

    @Test
    @SuppressWarnings("deprecation")
    void supportsTheLegacyMd5BodyDigest() {
        NotificationVerifier legacy =
                new NotificationVerifier(SECRET, BodyDigest.MD5, Duration.ofSeconds(60), fixedClock);
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, DATE, CALLBACK_URI);
        String md5Signature = new HmacSigner(SECRET, BodyDigest.MD5).sign(request);

        assertThatCode(() -> legacy.verify(request, md5Signature)).doesNotThrowAnyException();
        assertThatExceptionOfType(BankartSignatureException.class)
                .isThrownBy(() -> verifier.verify(request, md5Signature));
    }

    @Test
    void defaultsToASixtySecondWindowAgainstTheSystemClock() {
        NotificationVerifier defaults = new NotificationVerifier(SECRET);
        String now = HmacSigner.formatDate(Instant.now());
        SignedRequest request = SignedRequest.post(body, CONTENT_TYPE, now, CALLBACK_URI);

        assertThatCode(() -> defaults.verify(request, signatureFor(request))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingConstructorArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> new NotificationVerifier(SECRET, BodyDigest.SHA512, null, fixedClock));
        assertThatNullPointerException()
                .isThrownBy(() -> new NotificationVerifier(SECRET, BodyDigest.SHA512, Duration.ofSeconds(1), null));
        assertThatNullPointerException().isThrownBy(() -> verifier.verify(null, "sig"));
    }

    private static String signatureFor(SignedRequest request) {
        return new HmacSigner(SECRET).sign(request);
    }
}
