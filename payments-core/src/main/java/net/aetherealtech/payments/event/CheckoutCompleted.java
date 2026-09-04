package net.aetherealtech.payments.event;

import java.util.Map;
import java.util.Objects;

import net.aetherealtech.payments.Money;

/**
 * The buyer finished a hosted checkout and the provider considers it paid.
 *
 * <p>This, not the buyer's arrival at your success URL, is the outcome. A buyer who lands on the success
 * page has not necessarily paid — Bankart's documentation says so outright — and a buyer who never lands
 * there may well have. Fulfil on this event.
 *
 * <p>{@code merchantReference} is your own identifier, echoed back from
 * {@link net.aetherealtech.payments.PaymentIntent#merchantReference()}. It may be null where a provider
 * has no field to echo it in and the adapter could not recover it; {@code checkoutRef} always identifies
 * the checkout you started.
 *
 * @param header            identity, timing and acknowledgement
 * @param checkoutRef       the provider's handle for the checkout — {@link
 *                          net.aetherealtech.payments.RedirectTarget#checkoutRef()}
 * @param merchantReference your own order identifier, or null when the provider echoed none
 * @param amount            what was actually paid, which is not always what was asked for
 * @param paymentRef        the provider's handle for the payment, for a later refund; may be null
 * @param metadata          the metadata sent with the intent, as the provider returned it
 */
public record CheckoutCompleted(
        EventHeader header,
        String checkoutRef,
        String merchantReference,
        Money amount,
        String paymentRef,
        Map<String, String> metadata) implements PaymentEvent {

    public CheckoutCompleted {
        Objects.requireNonNull(header, "header must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Builder builder(final EventHeader header, final String checkoutRef) {
        return new Builder(header, checkoutRef);
    }

    public static final class Builder {
        private final EventHeader header;
        private final String checkoutRef;
        private String merchantReference;
        private Money amount;
        private String paymentRef;
        private Map<String, String> metadata;

        private Builder(final EventHeader header, final String checkoutRef) {
            this.header = header;
            this.checkoutRef = checkoutRef;
        }

        public Builder merchantReference(final String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public Builder amount(final Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder paymentRef(final String paymentRef) {
            this.paymentRef = paymentRef;
            return this;
        }

        public Builder metadata(final Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public CheckoutCompleted build() {
            return new CheckoutCompleted(header, checkoutRef, merchantReference, amount, paymentRef, metadata);
        }
    }
}
