package net.aetherealtech.payments;

import java.time.Instant;
import java.util.Objects;

/**
 * What came back from {@link PaymentProvider#refund(RefundRequest)}.
 *
 * <p><strong>Accepted is not settled.</strong> {@code pending} is true when the provider has taken the
 * refund but not yet moved the money, which for a card scheme is the normal case and can take days. A
 * consumer that marks an order refunded on this alone is right eventually and wrong in the meantime;
 * the {@link net.aetherealtech.payments.event.Refunded} event is what says it landed.
 *
 * @param refundRef  the provider's handle for the refund itself, distinct from the payment's; never blank
 * @param paymentRef the payment that was refunded
 * @param amount     what was actually refunded, which for a full refund may differ from the order total
 * @param pending    true when the provider accepted the refund but has not settled it
 * @param acceptedAt when the provider accepted it
 */
public record RefundReceipt(String refundRef, String paymentRef, Money amount, boolean pending, Instant acceptedAt) {

    public RefundReceipt {
        if (refundRef == null || refundRef.isBlank()) {
            throw new IllegalArgumentException("refundRef must not be blank");
        }
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }
}
