package net.aetherealtech.bankart.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The signature is the one part of this library that cannot be debugged from the outside: the
 * gateway's answer to a wrong one is "1004 Invalid signature" and nothing else. So these tests pin
 * the documented vector, and each deviation the documentation itself contains.
 *
 * <p>The expected values are the gateway's own published ones. They were reproduced independently
 * in Python before this class existed, which is what established that two of the doc's stated
 * inputs are not the inputs it actually used.
 */
class HmacSignerTest {

    private static final String SHARED_SECRET = "my-shared-secret";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String DATE = "Tue, 21 Jul 2020 13:15:03 UTC";

    /** As transmitted. The docs display this pretty-printed, but hash the compact form. */
    private static final String BODY =
            "{\"merchantTransactionId\":\"2019-09-02-0004\",\"amount\":\"9.99\",\"currency\":\"EUR\"}";

    private static final String BODY_SHA512 =
            "efe0b7cd39d6904dc90924b1a89629b14f11082ed2178cff562364ca0172318e"
            + "1535bb8766fbe66e8cc44d311eba806349bfe185607eca12d9d0f377a03ee617";

    /** The API key substituted, which is what the published signature was actually computed over. */
    private static final String REQUEST_URI = "/api/v3/transaction/my-api-key/debit";

    private static final String EXPECTED_SIGNATURE =
            "nL+8FBKWx4/pahYScKs/dRYPBEWjiBalRaWKHGtxLpELmLrgJ/+dSWjt6dZNuu6oF18NyWEU8tXLEVm2mtEapg==";

    private final HmacSigner signer = new HmacSigner(SHARED_SECRET);

    @Test
    @DisplayName("reproduces the signature published in the API reference")
    void documentedVector() {
        String signature = signer.sign("POST", body(), CONTENT_TYPE, DATE, REQUEST_URI);

        assertThat(signature).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    @DisplayName("the body hash is of the compact JSON, not of the docs' pretty-printed rendering")
    void bodyIsHashedAsTransmitted() {
        String prettyPrinted = "{ \"merchantTransactionId\": \"2019-09-02-0004\", \"amount\": \"9.99\","
                + " \"currency\": \"EUR\" }";

        assertThat(BodyDigest.SHA512.hashHex(body())).isEqualTo(BODY_SHA512);
        assertThat(BodyDigest.SHA512.hashHex(prettyPrinted.getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(BODY_SHA512);
    }

    @Test
    @DisplayName("the signed URI carries the substituted API key, not the {apiKey} placeholder")
    void uriPlaceholderIsSubstituted() {
        String withPlaceholder = signer.sign("POST", body(), CONTENT_TYPE, DATE,
                "/api/v3/transaction/{apiKey}/debit");

        assertThat(withPlaceholder).isNotEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    @DisplayName("the message is five components, newline-joined, with no trailing newline")
    void messageComposition() {
        String message = signer.canonicalMessage("POST", body(), CONTENT_TYPE, DATE, REQUEST_URI);

        assertThat(message).isEqualTo(String.join("\n", "POST", BODY_SHA512, CONTENT_TYPE, DATE, REQUEST_URI));
        assertThat(message).doesNotContain("\r");
        assertThat(message).doesNotEndWith("\n");
        assertThat(message.lines()).hasSize(5);
    }

    @Test
    @DisplayName("API v2's empty additional-headers line is gone")
    void noEmptyHeaderLine() {
        String withEmptyLine = String.join("\n", "POST", BODY_SHA512, CONTENT_TYPE, DATE, "", REQUEST_URI);

        assertThat(signer.canonicalMessage("POST", body(), CONTENT_TYPE, DATE, REQUEST_URI))
                .isNotEqualTo(withEmptyLine);
    }

    @Test
    void signsFromASignedRequest() {
        SignedRequest request = SignedRequest.post(BODY, CONTENT_TYPE, DATE, REQUEST_URI);

        assertThat(signer.sign(request)).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    void verifiesItsOwnSignature() {
        SignedRequest request = SignedRequest.post(BODY, CONTENT_TYPE, DATE, REQUEST_URI);

        assertThat(signer.verify(request, EXPECTED_SIGNATURE)).isTrue();
        assertThat(signer.verify(request, "not-the-signature")).isFalse();
        assertThat(signer.verify(request, null)).isFalse();
    }

    @Test
    @DisplayName("a single changed byte anywhere changes the signature")
    void everyComponentIsCovered() {
        assertThat(signer.sign("GET", body(), CONTENT_TYPE, DATE, REQUEST_URI)).isNotEqualTo(EXPECTED_SIGNATURE);
        assertThat(signer.sign("POST", "{}".getBytes(StandardCharsets.UTF_8), CONTENT_TYPE, DATE, REQUEST_URI))
                .isNotEqualTo(EXPECTED_SIGNATURE);
        assertThat(signer.sign("POST", body(), "application/json", DATE, REQUEST_URI)).isNotEqualTo(EXPECTED_SIGNATURE);
        assertThat(signer.sign("POST", body(), CONTENT_TYPE, "Tue, 21 Jul 2020 13:15:04 UTC", REQUEST_URI))
                .isNotEqualTo(EXPECTED_SIGNATURE);
        assertThat(signer.sign("POST", body(), CONTENT_TYPE, DATE, "/api/v3/transaction/my-api-key/refund"))
                .isNotEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        String other = new HmacSigner("another-secret").sign("POST", body(), CONTENT_TYPE, DATE, REQUEST_URI);

        assertThat(other).isNotEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    @DisplayName("the signature is Base64 of the raw MAC, not of its hex")
    void base64OfBinaryMac() {
        byte[] decoded = java.util.Base64.getDecoder().decode(EXPECTED_SIGNATURE);

        assertThat(decoded).hasSize(64);
    }

    @Test
    void formatsDatesAsImfFixdate() {
        Instant instant = Instant.parse("2020-07-21T13:15:03Z");

        assertThat(HmacSigner.formatDate(instant)).isEqualTo("Tue, 21 Jul 2020 13:15:03 GMT");
    }

    @Test
    @SuppressWarnings("deprecation")
    void md5BodyDigestIsAvailableForLegacyDeployments() {
        HmacSigner legacy = new HmacSigner(SHARED_SECRET, BodyDigest.MD5);

        assertThat(legacy.canonicalMessage("POST", body(), CONTENT_TYPE, DATE, REQUEST_URI))
                .contains(BodyDigest.MD5.hashHex(body()))
                .doesNotContain(BODY_SHA512);
    }

    @Test
    void rejectsAnAbsentOrEmptySecret() {
        assertThatNullPointerException().isThrownBy(() -> new HmacSigner(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new HmacSigner(""));
    }

    @Test
    void rejectsMissingComponents() {
        assertThatNullPointerException()
                .isThrownBy(() -> signer.canonicalMessage(null, body(), CONTENT_TYPE, DATE, REQUEST_URI));
        assertThatNullPointerException()
                .isThrownBy(() -> signer.canonicalMessage("POST", null, CONTENT_TYPE, DATE, REQUEST_URI));
        assertThatNullPointerException()
                .isThrownBy(() -> signer.canonicalMessage("POST", body(), null, DATE, REQUEST_URI));
        assertThatNullPointerException()
                .isThrownBy(() -> signer.canonicalMessage("POST", body(), CONTENT_TYPE, null, REQUEST_URI));
        assertThatNullPointerException()
                .isThrownBy(() -> signer.canonicalMessage("POST", body(), CONTENT_TYPE, DATE, null));
    }

    @Test
    @DisplayName("SignedRequest copies the body so a later mutation cannot change what was signed")
    void signedRequestDefensivelyCopies() {
        byte[] mutable = BODY.getBytes(StandardCharsets.UTF_8);
        SignedRequest request = new SignedRequest("POST", mutable, CONTENT_TYPE, DATE, REQUEST_URI);

        mutable[0] = 'X';

        assertThat(signer.sign(request)).isEqualTo(EXPECTED_SIGNATURE);
        assertThat(request.body()).isNotSameAs(request.body());
    }

    private static byte[] body() {
        return BODY.getBytes(StandardCharsets.UTF_8);
    }
}
