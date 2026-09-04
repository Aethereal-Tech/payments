package net.aetherealtech.payments.bankart.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The gateway's request signature: HMAC-SHA512 over five newline-joined components, Base64 of the
 * raw MAC bytes.
 *
 * <p>The message is
 * <pre>
 * METHOD \n sha512hex(body) \n Content-Type \n Date \n request-URI
 * </pre>
 * with a bare {@code \n} (no carriage return), no trailing newline, and — unlike API v2 — no empty
 * line for additional headers.
 *
 * <p>Two details are worth stating because the published worked example gets them wrong, and both
 * are pinned by tests in {@code HmacSignerTest}:
 *
 * <ul>
 *   <li>The body that is hashed is the exact byte sequence transmitted. The docs render their
 *       example body pretty-printed, but the hash they publish is of the compact form — so a signer
 *       that hashes anything other than the bytes it is about to write will produce a valid-looking
 *       signature the gateway rejects.</li>
 *   <li>The request URI carries the <em>substituted</em> API key. The docs' concatenation block
 *       prints the literal {@code /api/v3/transaction/{apiKey}/debit}, yet their published signature
 *       only reproduces from {@code /api/v3/transaction/my-api-key/debit}.</li>
 * </ul>
 */
public final class HmacSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA512";

    /**
     * RFC 7231 IMF-fixdate, which is what the {@code Date} header must carry. The API reference's
     * signature example prints a "UTC" suffix instead of "GMT"; that is a formatting slip in the
     * docs, and immaterial either way because the signature covers whatever string the header
     * holds — the only hard requirement is that the two agree.
     */
    private static final DateTimeFormatter IMF_FIXDATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final byte[] sharedSecret;
    private final BodyDigest bodyDigest;

    public HmacSigner(String sharedSecret) {
        this(sharedSecret, BodyDigest.SHA512);
    }

    public HmacSigner(String sharedSecret, BodyDigest bodyDigest) {
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        if (sharedSecret.isEmpty()) {
            throw new IllegalArgumentException("sharedSecret must not be empty");
        }
        this.sharedSecret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        this.bodyDigest = Objects.requireNonNull(bodyDigest, "bodyDigest");
    }

    public static String formatDate(Instant instant) {
        return IMF_FIXDATE.format(instant);
    }

    /**
     * The exact string that is fed to the MAC. Exposed because when a signature is rejected this is
     * the only thing worth comparing against the gateway's own view.
     */
    public String canonicalMessage(String method, byte[] body, String contentType, String date, String requestUri) {
        return String.join("\n",
                Objects.requireNonNull(method, "method"),
                bodyDigest.hashHex(Objects.requireNonNull(body, "body")),
                Objects.requireNonNull(contentType, "contentType"),
                Objects.requireNonNull(date, "date"),
                Objects.requireNonNull(requestUri, "requestUri"));
    }

    public String sign(String method, byte[] body, String contentType, String date, String requestUri) {
        return signMessage(canonicalMessage(method, body, contentType, date, requestUri));
    }

    public String sign(SignedRequest request) {
        Objects.requireNonNull(request, "request");
        return sign(request.method(), request.body(), request.contentType(), request.date(), request.requestUri());
    }

    /**
     * Constant-time comparison against a signature we did not produce, so that a mismatching prefix
     * cannot be found one byte at a time by timing the rejections.
     */
    public boolean verify(SignedRequest request, String presentedSignature) {
        if (presentedSignature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(request).getBytes(StandardCharsets.UTF_8),
                presentedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private String signMessage(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedSecret, HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 is required of every JRE", e);
        }
    }
}
