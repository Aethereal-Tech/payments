package net.aetherealtech.payments.exception;

/**
 * The instrument said no. A business outcome, not an integration fault.
 *
 * <p>Distinct from {@link PaymentProviderException} because the audience differs: a decline is something
 * to tell the BUYER ("your card was declined, try another"), while a provider failure is something to
 * tell an operator. Retrying a decline unchanged achieves nothing and, on a card scheme, retrying it
 * repeatedly is how an account attracts attention.
 *
 * <p>{@code declineCode} is the provider's own code where it published one, and it is what logic should
 * branch on. Decline MESSAGES are free text that providers reserve the right to reword.
 */
public class PaymentDeclinedException extends PaymentException {

    private final String provider;
    private final String declineCode;

    public PaymentDeclinedException(final String message, final String provider, final String declineCode) {
        super(message);
        this.provider = provider;
        this.declineCode = declineCode;
    }

    /** Which adapter raised this, matching {@link net.aetherealtech.payments.PaymentProvider#id()}. */
    public String provider() {
        return provider;
    }

    /** The provider's own decline code, or null when it published none. Never branch on the message. */
    public String declineCode() {
        return declineCode;
    }
}
