package net.aetherealtech.payments;

import java.util.Objects;
import java.util.Optional;

/**
 * Where to send the buyer for a hosted checkout, and the handle to persist before you do.
 *
 * <p>Persist {@link #checkoutRef()} against your order BEFORE redirecting. The webhook that decides
 * whether the payment happened arrives independently of the buyer's browser and may arrive first; it
 * identifies the payment by this reference.
 *
 * <p>{@code machinePaymentUrl} is the same purchase offered to something that is not a browser — an
 * agent paying over HTTP 402, which is AgentaOS's {@code x402Url}. It is empty for a provider that has
 * no such rail, which is why it is an {@link Optional} accessor rather than a field a caller might
 * publish as a link without checking.
 *
 * @param redirectUrl       where to send the buyer; never blank
 * @param iframe            true when the page is meant to be embedded rather than navigated to
 * @param machinePaymentUrl a non-browser payment entry point, or null when the provider has none
 * @param checkoutRef       the provider's handle for this checkout — what a retrieve, cancel or later
 *                          refund keys on, and what the webhook will name
 */
public record RedirectTarget(String redirectUrl, boolean iframe, String machinePaymentUrl, String checkoutRef) {

    public RedirectTarget {
        Objects.requireNonNull(redirectUrl, "redirectUrl must not be null");
        if (redirectUrl.isBlank()) {
            throw new IllegalArgumentException("redirectUrl must not be blank");
        }
    }

    /** A plain browser redirect from a provider with no machine-payment rail. */
    public static RedirectTarget of(final String redirectUrl, final String checkoutRef) {
        return new RedirectTarget(redirectUrl, false, null, checkoutRef);
    }

    /** The machine-payment entry point, present only where the provider offers one. */
    public Optional<String> machinePayment() {
        return Optional.ofNullable(machinePaymentUrl);
    }
}
