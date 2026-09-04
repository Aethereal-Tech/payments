package net.aetherealtech.payments.exception;

/**
 * The provider or the transport failed: the request was refused, malformed, unauthorised, rate-limited,
 * or never answered at all.
 *
 * <p><strong>{@link #outcomeUnknown()} is the field that matters.</strong> A timeout is not a failure —
 * it is an absence of news, and the payment may well have gone through. Retrying a payment whose outcome
 * is unknown is how a buyer is charged twice; the recovery is a status lookup or a
 * {@link net.aetherealtech.payments.PaymentProvider#reconcile(String)}, keyed on the caller's own
 * {@code merchantReference}. A refusal the provider actually stated
 * ({@code outcomeUnknown() == false}) did not move money and is safe to correct and retry.
 *
 * <p>{@code code} is the provider's own error code where it published one, and is what logic should
 * branch on — Bankart's docs are explicit that {@code errorMessage} may be reworded at any time, and
 * AgentaOS's error body carries free text too.
 */
public class PaymentProviderException extends PaymentException {

    private final String provider;
    private final String code;
    private final boolean outcomeUnknown;

    public PaymentProviderException(
            final String message, final String provider, final String code, final Throwable cause) {
        this(message, provider, code, false, cause);
    }

    public PaymentProviderException(
            final String message,
            final String provider,
            final String code,
            final boolean outcomeUnknown,
            final Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.code = code;
        this.outcomeUnknown = outcomeUnknown;
    }

    /** Which adapter raised this, matching {@link net.aetherealtech.payments.PaymentProvider#id()}. */
    public String provider() {
        return provider;
    }

    /** The provider's own error code, or null when it published none. Never branch on the message. */
    public String code() {
        return code;
    }

    /**
     * True when the request may have been processed despite the failure — a timeout, a dropped
     * connection, an interruption. Recover by asking, never by repeating.
     */
    public boolean outcomeUnknown() {
        return outcomeUnknown;
    }
}
