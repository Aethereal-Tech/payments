package net.aetherealtech.payments.bankart.model;

import java.util.Objects;

/**
 * A started hosted-checkout: where to send the customer, and the two identifiers to persist before
 * you do.
 *
 * <p>Storing {@code uuid} against your order <em>before</em> the redirect is what makes the
 * subsequent notification matchable. It is also the handle for a capture, refund or recurring
 * charge later on.
 */
public record RedirectResult(
        String redirectUrl,
        RedirectType redirectType,
        String uuid,
        String purchaseId,
        String paymentMethod) {

    public RedirectResult {
        Objects.requireNonNull(redirectUrl, "redirectUrl");
    }

    public static RedirectResult from(TransactionResponse response) {
        return new RedirectResult(
                response.redirectUrl(),
                response.redirectType(),
                response.uuid(),
                response.purchaseId(),
                response.paymentMethod());
    }

    /** True when the payment page is meant to be embedded rather than navigated to. */
    public boolean isIframe() {
        return redirectType == RedirectType.IFRAME;
    }
}
