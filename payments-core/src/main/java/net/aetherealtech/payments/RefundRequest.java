package net.aetherealtech.payments;

import java.util.Objects;
import java.util.Optional;

/**
 * Ask to give money back, in whole or in part.
 *
 * <p>{@code amount} is null for a full refund and set for a partial one. Null rather than "the whole
 * amount" spelled out, because a caller that has to state the total is a caller that can state the
 * wrong total — the provider knows what was charged, including the reconciliation restatements that
 * make its figure differ from the one on the order.
 *
 * <p>{@code merchantReference} is the caller's own identifier for THIS refund, not for the original
 * payment. Where a provider accepts one it becomes the idempotency handle: the same reference sent
 * twice is one refund, which is what stands between a retried request and a buyer paid back twice.
 *
 * @param paymentRef        the provider's handle for the payment being refunded; never blank
 * @param amount            how much to give back, or null to refund everything
 * @param merchantReference the caller's own identifier for this refund, or null
 * @param reason            free text for the provider's records and the buyer's statement, or null
 */
public record RefundRequest(String paymentRef, Money amount, String merchantReference, String reason) {

    public RefundRequest {
        if (paymentRef == null || paymentRef.isBlank()) {
            throw new IllegalArgumentException("paymentRef must not be blank");
        }
    }

    /** Give all of it back. */
    public static RefundRequest full(final String paymentRef) {
        return new RefundRequest(paymentRef, null, null, null);
    }

    /** Give part of it back. */
    public static RefundRequest partial(final String paymentRef, final Money amount) {
        return new RefundRequest(paymentRef, Objects.requireNonNull(amount, "amount must not be null"), null, null);
    }

    public RefundRequest withMerchantReference(final String merchantReference) {
        return new RefundRequest(paymentRef, amount, merchantReference, reason);
    }

    public RefundRequest withReason(final String reason) {
        return new RefundRequest(paymentRef, amount, merchantReference, reason);
    }

    /** True when no amount was named, meaning the whole payment. */
    public boolean isFull() {
        return amount == null;
    }

    /** The partial amount, when one was named. */
    public Optional<Money> partialAmount() {
        return Optional.ofNullable(amount);
    }
}
