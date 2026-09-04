package net.aetherealtech.payments.exception;

/**
 * Base of every failure this SPI raises, whichever provider raised it.
 *
 * <p>Unchecked, because at the call sites that matter — a checkout controller, a webhook endpoint, a
 * scheduled reconcile — there is nothing useful to do with most of these but let them travel. What the
 * subtypes carry is the distinction a caller can actually act on: tell the buyer
 * ({@link PaymentDeclinedException}), fix the call or retry it ({@link PaymentProviderException}),
 * refuse to trust the request ({@link WebhookVerificationException}), or check
 * {@link net.aetherealtech.payments.PaymentProvider#capabilities()} first
 * ({@link UnsupportedCapabilityException}).
 *
 * <p><strong>Every adapter translates its own exceptions into these at the SPI boundary.</strong> A
 * caller holding a {@code PaymentProvider} can catch {@code PaymentException} and be sure nothing
 * provider-shaped escapes. The adapters' native exception types stay public and reachable below the SPI,
 * for a caller that has deliberately reached for one specific gateway's client.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(final String message) {
        super(message);
    }

    public PaymentException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
