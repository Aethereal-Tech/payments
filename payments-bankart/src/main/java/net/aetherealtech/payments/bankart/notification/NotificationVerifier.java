package net.aetherealtech.payments.bankart.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import net.aetherealtech.payments.bankart.exception.BankartSignatureException;
import net.aetherealtech.payments.bankart.signing.BodyDigest;
import net.aetherealtech.payments.bankart.signing.HmacSigner;
import net.aetherealtech.payments.bankart.signing.SignedRequest;

/**
 * Authenticates an inbound notification before anything acts on it.
 *
 * <p>The docs specify that the gateway signs every notification with the same shared secret and the
 * same five-component scheme as outbound requests, with the values taken from the received request,
 * and that the {@code Date} should be checked against the current time — they suggest 60 seconds,
 * which is the default here.
 *
 * <p>Where the docs stop short, this class is deliberately strict rather than accommodating:
 *
 * <ul>
 *   <li>They do not name the header the signature arrives in. {@code X-Signature} is assumed, by
 *       symmetry with the request direction; {@link #SIGNATURE_HEADER} is exposed so a caller who
 *       observes otherwise can read it from elsewhere and still use this class, which takes the
 *       value rather than the request.</li>
 *   <li>They do not say which URI form is signed — the callback's path alone, or the path with the
 *       query string that {@code callbackUrl} may carry. The caller passes the
 *       {@link SignedRequest#requestUri()} it believes was signed, so both are reachable; start with
 *       path-and-query, since that is what the merchant registered.</li>
 *   <li>"When no signature is sent with the request, the payload will be hashed using MD5" describes
 *       the unsigned case, in which there is nothing to verify. This class never treats a missing
 *       signature as acceptable — {@link BodyDigest#MD5} exists only for a deployment observed to
 *       sign that way.</li>
 * </ul>
 *
 * <p>An unverifiable notification must not advance an order. It is indistinguishable from an
 * attacker telling you a payment succeeded.
 */
public final class NotificationVerifier {

    /** Assumed by symmetry with the request signature; not stated in the documentation. */
    public static final String SIGNATURE_HEADER = "X-Signature";

    private static final Duration DEFAULT_MAX_SKEW = Duration.ofSeconds(60);

    /** Accepts both the RFC 7231 "GMT" the header should carry and the "UTC" the docs' examples print. */
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'UTC'", java.util.Locale.ENGLISH)
                    .withZone(java.time.ZoneOffset.UTC)
    };

    private final HmacSigner signer;
    private final NotificationParser parser;
    private final Duration maxSkew;
    private final Clock clock;

    public NotificationVerifier(String sharedSecret) {
        this(sharedSecret, BodyDigest.SHA512, DEFAULT_MAX_SKEW, Clock.systemUTC());
    }

    public NotificationVerifier(String sharedSecret, BodyDigest bodyDigest, Duration maxSkew, Clock clock) {
        this.signer = new HmacSigner(sharedSecret, bodyDigest);
        this.parser = new NotificationParser();
        this.maxSkew = Objects.requireNonNull(maxSkew, "maxSkew");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @throws BankartSignatureException if the signature is absent, does not match, or the request's
     *                                   Date is outside the accepted window
     */
    public void verify(SignedRequest request, String presentedSignature) {
        Objects.requireNonNull(request, "request");
        if (presentedSignature == null || presentedSignature.isBlank()) {
            throw new BankartSignatureException("Notification carried no " + SIGNATURE_HEADER + " header");
        }
        requireFreshDate(request.date());
        if (!signer.verify(request, presentedSignature)) {
            throw new BankartSignatureException("Notification signature did not match");
        }
    }

    /** Verify first, parse second — nothing should read a payload it has not authenticated. */
    public Notification verifyAndParse(SignedRequest request, String presentedSignature) {
        verify(request, presentedSignature);
        return parser.parse(request.body());
    }

    private void requireFreshDate(String date) {
        Instant sent = parseDate(date);
        Duration skew = Duration.between(sent, clock.instant()).abs();
        if (skew.compareTo(maxSkew) > 0) {
            throw new BankartSignatureException(
                    "Notification Date " + date + " is " + skew.toSeconds() + "s away from now, beyond the "
                            + maxSkew.toSeconds() + "s window");
        }
    }

    private static Instant parseDate(String date) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(date, format).toInstant();
            } catch (DateTimeParseException ignored) {
                // try the next accepted spelling
            }
        }
        throw new BankartSignatureException("Notification Date header is unparseable: " + date);
    }
}
