package net.aetherealtech.payments.exception;

/**
 * An inbound webhook did not authenticate: no signature, a signature that did not match, or a timestamp
 * outside the accepted window.
 *
 * <p>Treat as hostile input, not as a transient fault. Nothing in the payload may be read, let alone
 * acted on — an unverifiable webhook is indistinguishable from an attacker asserting that a payment
 * succeeded. Answer the request with a non-2xx and change no state.
 *
 * <p>{@link #reason()} says WHICH component failed, and exists for one reason: at least one gateway has
 * shipped a release that mis-formatted its own callbacks for some connector configurations. An operator
 * staring at "signature did not match" cannot tell that from an attack; one reading "the Date header is
 * 4 000s from now" or "no X-Signature header was present" can. It never softens the refusal — an
 * unverified payload is refused with a precise reason, not accepted with a warning.
 */
public class WebhookVerificationException extends PaymentException {

    private final String provider;
    private final String reason;

    public WebhookVerificationException(final String message, final String provider, final String reason) {
        super(message);
        this.provider = provider;
        this.reason = reason;
    }

    /** Which adapter raised this, matching {@link net.aetherealtech.payments.PaymentProvider#id()}. */
    public String provider() {
        return provider;
    }

    /** Which component of the verification failed, for an operator reading a log. Never null. */
    public String reason() {
        return reason;
    }
}
