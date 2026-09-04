package net.aetherealtech.payments.agentaos;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.exception.WebhookVerificationException;

/**
 * Checks that an inbound webhook really came from AgentaOS, before anything reads it.
 *
 * <p>The scheme, in full: a header {@code x-agentaos-signature} carries
 * {@code t=<unix seconds>,v1=<hex>}, and {@code v1} is the HMAC-SHA256, keyed with the webhook secret,
 * of the string formed by the timestamp digits, a literal {@code .}, and the RAW request body. Raw
 * means the exact octets received — a body that has been parsed and re-serialised is not the same string
 * and will not verify, however identical it looks, which is the single most common way a working
 * integration is reported as broken.
 *
 * <p>A timestamp in the FUTURE is refused as well as a stale one. That is not symmetry for its own sake:
 * a far-future timestamp is how a captured request is made to keep verifying indefinitely.
 *
 * <p>Every refusal is a {@link WebhookVerificationException} whose
 * {@link WebhookVerificationException#reason()} names the component that failed. Nothing here ever
 * returns a payload it could not authenticate.
 */
public final class AgentaOsWebhookVerifier {

    /**
     * The header the signature travels in.
     *
     * <p>Lower case because HTTP header names are case-insensitive and {@link InboundWebhook} looks them
     * up that way.
     */
    @Provisional("packages/pay/src never reads this header — verify() takes the signature as a string "
            + "parameter — so the name comes from examples/pay-express-shop/server.ts "
            + "(req.headers['x-agentaos-signature']) and the same line in packages/pay/README.md.")
    public static final String SIGNATURE_HEADER = "x-agentaos-signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TIMESTAMP_KEY = "t";
    private static final String SIGNATURE_KEY = "v1";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    /** With the SDK's own 300-second tolerance. */
    public AgentaOsWebhookVerifier(final String secret) {
        this(secret, AgentaOsConfig.DEFAULT_WEBHOOK_TOLERANCE);
    }

    public AgentaOsWebhookVerifier(final String secret, final Duration tolerance) {
        this(secret, tolerance, Clock.systemUTC());
    }

    public AgentaOsWebhookVerifier(final String secret, final Duration tolerance, final Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("the webhook secret must not be blank");
        }
        Objects.requireNonNull(tolerance, "tolerance must not be null");
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Verifies {@code webhook}, answering the timestamp it was signed at.
     *
     * <p>That timestamp is the only time AgentaOS states about the delivery — the payloads carry no
     * event time of their own — so it becomes the event's {@code occurredAt} and part of its synthesised
     * identifier.
     *
     * @return the signed time, in seconds since the epoch
     * @throws WebhookVerificationException naming the component that failed
     */
    public long verify(final InboundWebhook webhook) {
        Objects.requireNonNull(webhook, "webhook must not be null");
        final String header = webhook.header(SIGNATURE_HEADER)
                .orElseThrow(() -> refuse("no " + SIGNATURE_HEADER + " header was present",
                        "signature_header_absent"));

        final String timestampText = element(header, TIMESTAMP_KEY);
        final String received = element(header, SIGNATURE_KEY);
        if (timestampText == null || received == null || received.isEmpty()) {
            throw refuse("the " + SIGNATURE_HEADER + " header is not of the form t=<seconds>,v1=<hex>",
                    "signature_header_malformed");
        }

        final long timestamp;
        try {
            timestamp = Long.parseLong(timestampText.trim());
        } catch (NumberFormatException e) {
            throw refuse("the signature's t= element is not a number of seconds: \"" + timestampText + "\"",
                    "timestamp_unparseable");
        }

        final long age = clock.instant().getEpochSecond() - timestamp;
        if (age > tolerance.toSeconds() || age < 0) {
            throw refuse("the signature's timestamp is " + age + "s from now, outside the "
                    + tolerance.toSeconds() + "s tolerance", "timestamp_outside_tolerance");
        }

        final byte[] signedContent = signedContent(timestamp, webhook.body());
        final String expected = HexFormat.of().formatHex(hmacSha256(signedContent));
        // Length first, then a constant-time comparison of the hex TEXT. Comparing the text rather than
        // the decoded bytes means a v1 that is not valid hex simply fails to match, instead of throwing
        // out of a decoder before the comparison happens; lower-casing keeps upper-case hex accepted,
        // which decoding to bytes would have done implicitly.
        final String candidate = received.toLowerCase(Locale.ROOT);
        if (expected.length() != candidate.length()
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                        candidate.getBytes(StandardCharsets.US_ASCII))) {
            throw refuse("the signature does not match the body", "signature_mismatch");
        }
        return timestamp;
    }

    private static byte[] signedContent(final long timestamp, final byte[] body) {
        final byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
        final byte[] out = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(body, 0, out, prefix.length, body.length);
        return out;
    }

    private byte[] hmacSha256(final byte[] message) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is required of every JRE, so this is a broken installation rather than input.
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String element(final String header, final String key) {
        for (final String part : header.split(",")) {
            final int equals = part.indexOf('=');
            if (equals < 0) {
                continue;
            }
            if (part.substring(0, equals).trim().equals(key)) {
                // Everything after the FIRST '=' is the value, so a base64-ish tail carrying '=' survives.
                return part.substring(equals + 1);
            }
        }
        return null;
    }

    private static WebhookVerificationException refuse(final String message, final String reason) {
        return new WebhookVerificationException(
                "AgentaOS webhook rejected: " + message + ".", AgentaOsPaymentProvider.PROVIDER_ID, reason);
    }
}
