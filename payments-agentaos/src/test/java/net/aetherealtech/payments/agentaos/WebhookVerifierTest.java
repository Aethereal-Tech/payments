package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/**
 * The signature scheme, pinned by vectors this test computes for itself.
 *
 * <p>{@link #hmacHex} is a second, deliberately plain implementation, and
 * {@link #PUBLISHED_VECTOR} was derived outside this codebase entirely. Verifying the class under test
 * against its own arithmetic would prove only that it is self-consistent, which is exactly what a
 * signature check must not be.
 */
class WebhookVerifierTest {

    static final String SECRET = "whsec_test_secret";
    static final long TIMESTAMP = 1_788_434_542L;
    static final String BODY = "{\"type\":\"ping\"}";

    /** HMAC-SHA256 of {@code 1788434542.{"type":"ping"}} keyed with {@link #SECRET}, derived offline. */
    private static final String PUBLISHED_VECTOR =
            "eb3957e6f48f6554726129ffa08d05a13ae8220cca7cbcd3abb0ec93f3283f80";

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(TIMESTAMP + 5), ZoneOffset.UTC);

    @Test
    void signsTheTimestampADotAndTheRawBody() {
        assertThat(hmacHex(SECRET, TIMESTAMP + "." + BODY)).isEqualTo(PUBLISHED_VECTOR);

        assertThat(verifier().verify(webhook(header(TIMESTAMP, PUBLISHED_VECTOR), BODY)))
                .isEqualTo(TIMESTAMP);
    }

    @Test
    void acceptsUpperCaseHexTheWayDecodingToBytesWould() {
        final String upper = PUBLISHED_VECTOR.toUpperCase(Locale.ROOT);

        assertThat(verifier().verify(webhook(header(TIMESTAMP, upper), BODY))).isEqualTo(TIMESTAMP);
    }

    @Test
    void aBodyThatWasReserialisedDoesNotVerify() {
        // The same document, re-spaced. Every signature scheme here covers the exact octets transmitted,
        // and a JSON body parser running before the handler is how a working integration is reported broken.
        final String reserialised = "{ \"type\": \"ping\" }";

        assertThat(reason(header(TIMESTAMP, PUBLISHED_VECTOR), reserialised)).isEqualTo("signature_mismatch");
    }

    @Test
    void refusesEveryComponentByName() {
        assertThat(reasonWithHeaders(Map.of())).isEqualTo("signature_header_absent");
        assertThat(reason("v1=" + PUBLISHED_VECTOR)).isEqualTo("signature_header_malformed");
        assertThat(reason("t=" + TIMESTAMP)).isEqualTo("signature_header_malformed");
        assertThat(reason("t=" + TIMESTAMP + ",v1=")).isEqualTo("signature_header_malformed");
        assertThat(reason("garbage")).isEqualTo("signature_header_malformed");
        assertThat(reason("t=lastweek,v1=" + PUBLISHED_VECTOR)).isEqualTo("timestamp_unparseable");
        assertThat(reason(header(TIMESTAMP, "0".repeat(64)))).isEqualTo("signature_mismatch");
        assertThat(reason(header(TIMESTAMP, "abc"))).isEqualTo("signature_mismatch");
        assertThat(reason(header(TIMESTAMP, "zz" + PUBLISHED_VECTOR.substring(2))))
                .isEqualTo("signature_mismatch");
    }

    @Test
    void refusesAStaleTimestamp() {
        final long old = TIMESTAMP - 301;

        assertThat(reason(header(old, hmacHex(SECRET, old + "." + BODY))))
                .isEqualTo("timestamp_outside_tolerance");
    }

    @Test
    void refusesAFutureTimestampToo() {
        // Not symmetry for its own sake: a far-future timestamp is how a captured request is made to
        // keep verifying indefinitely.
        final long ahead = TIMESTAMP + 6;

        final WebhookVerificationException e = catchThrowableOfType(WebhookVerificationException.class,
                () -> verifier().verify(webhook(header(ahead, hmacHex(SECRET, ahead + "." + BODY)), BODY)));

        assertThat(e.reason()).isEqualTo("timestamp_outside_tolerance");
        assertThat(e).hasMessageContaining("-1s from now");
    }

    @Test
    void acceptsTheEdgesOfTheWindow() {
        final long edge = TIMESTAMP - 295;
        final AgentaOsWebhookVerifier verifier =
                new AgentaOsWebhookVerifier(SECRET, Duration.ofSeconds(300), clock);

        assertThat(verifier.verify(webhook(header(edge, hmacHex(SECRET, edge + "." + BODY)), BODY)))
                .isEqualTo(edge);
    }

    @Test
    void honoursANarrowedTolerance() {
        final long recent = TIMESTAMP - 4;
        final AgentaOsWebhookVerifier strict =
                new AgentaOsWebhookVerifier(SECRET, Duration.ofSeconds(2), clock);

        assertThat(catchThrowableOfType(WebhookVerificationException.class,
                () -> strict.verify(webhook(header(recent, hmacHex(SECRET, recent + "." + BODY)), BODY)))
                .reason())
                .isEqualTo("timestamp_outside_tolerance");
    }

    @Test
    void findsTheHeaderWhateverCaseAProxyLeftItIn() {
        final InboundWebhook webhook = InboundWebhook.post(
                BODY.getBytes(StandardCharsets.UTF_8), "/hooks/agentaos",
                Map.of("X-AgentaOS-Signature", header(TIMESTAMP, PUBLISHED_VECTOR)));

        assertThat(verifier().verify(webhook)).isEqualTo(TIMESTAMP);
    }

    @Test
    void refusesToBeBuiltWithoutASecretOrWithANegativeTolerance() {
        assertThatThrownBy(() -> new AgentaOsWebhookVerifier(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret must not be blank");
        assertThatThrownBy(() -> new AgentaOsWebhookVerifier(SECRET, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerance");
        assertThatThrownBy(() -> new AgentaOsWebhookVerifier(SECRET, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentaOsWebhookVerifier(SECRET, Duration.ofSeconds(1), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentaOsWebhookVerifier(SECRET).verify(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void namesTheHeaderTheExampleIntegrationReads() {
        assertThat(AgentaOsWebhookVerifier.SIGNATURE_HEADER).isEqualTo("x-agentaos-signature");
    }

    // ---------------------------------------------------------------- helpers

    static String header(final long timestamp, final String signature) {
        return "t=" + timestamp + ",v1=" + signature;
    }

    static InboundWebhook webhook(final String signatureHeader, final String body) {
        return InboundWebhook.post(body.getBytes(StandardCharsets.UTF_8), "/hooks/agentaos",
                signatureHeader == null
                        ? Map.of()
                        : Map.of(AgentaOsWebhookVerifier.SIGNATURE_HEADER, signatureHeader));
    }

    /** A second HMAC implementation, written plainly so that agreeing with it means something. */
    static String hmacHex(final String secret, final String message) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            final StringBuilder out = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private AgentaOsWebhookVerifier verifier() {
        return new AgentaOsWebhookVerifier(SECRET, Duration.ofSeconds(300), clock);
    }

    private String reason(final String signatureHeader) {
        return reason(signatureHeader, BODY);
    }

    private String reason(final String signatureHeader, final String body) {
        return catchThrowableOfType(WebhookVerificationException.class,
                () -> verifier().verify(webhook(signatureHeader, body))).reason();
    }

    private String reasonWithHeaders(final Map<String, String> headers) {
        final InboundWebhook webhook =
                InboundWebhook.post(BODY.getBytes(StandardCharsets.UTF_8), "/hooks/agentaos", headers);
        return catchThrowableOfType(WebhookVerificationException.class,
                () -> verifier().verify(webhook)).reason();
    }
}
