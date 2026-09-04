package net.aetherealtech.payments.exception;

import net.aetherealtech.payments.ProviderCapability;

/**
 * An operation was called on a provider that does not declare the capability it needs.
 *
 * <p>A {@link PaymentException} rather than an {@link UnsupportedOperationException} on purpose: a
 * caller that wraps its payment work in {@code catch (PaymentException)} — which is the arrangement this
 * SPI's exception hierarchy exists to make possible — would otherwise have exactly one refusal escape
 * it, and would meet it in production rather than in a test.
 *
 * <p>Reaching this is a programming error and not a runtime condition to handle: ask
 * {@link net.aetherealtech.payments.PaymentProvider#supports(ProviderCapability)} first.
 */
public class UnsupportedCapabilityException extends PaymentException {

    private final String provider;
    private final ProviderCapability capability;

    public UnsupportedCapabilityException(final String provider, final ProviderCapability capability) {
        super("Provider \"" + provider + "\" does not support " + capability);
        this.provider = provider;
        this.capability = capability;
    }

    /** Which adapter raised this, matching {@link net.aetherealtech.payments.PaymentProvider#id()}. */
    public String provider() {
        return provider;
    }

    /** The capability that was needed and not declared. */
    public ProviderCapability capability() {
        return capability;
    }
}
