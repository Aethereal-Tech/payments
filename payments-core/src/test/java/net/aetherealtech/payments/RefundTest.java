package net.aetherealtech.payments;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RefundTest {

    @Nested
    class RefundRequestTests {

        @Test
        void refusesANullPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RefundRequest(null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void refusesABlankPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RefundRequest("   ", null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void fullRefundsEverythingAndCarriesNoAmount() {
            final RefundRequest request = RefundRequest.full("payment-1");

            assertThat(request.isFull()).isTrue();
            assertThat(request.partialAmount()).isEmpty();
        }

        @Test
        void partialRefusesANullAmount() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RefundRequest.partial("payment-1", null))
                    .withMessageContaining("amount");
        }

        @Test
        void partialCarriesTheStatedAmount() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final RefundRequest request = RefundRequest.partial("payment-1", amount);

            assertThat(request.isFull()).isFalse();
            assertThat(request.partialAmount()).contains(amount);
        }

        @Test
        void withMerchantReferenceLeavesTheOriginalUntouched() {
            final RefundRequest original = RefundRequest.full("payment-1");
            final RefundRequest withReference = original.withMerchantReference("refund-1");

            assertThat(original.merchantReference()).isNull();
            assertThat(withReference.merchantReference()).isEqualTo("refund-1");
            assertThat(withReference.paymentRef()).isEqualTo("payment-1");
        }

        @Test
        void withReasonLeavesTheOriginalUntouched() {
            final RefundRequest original = RefundRequest.full("payment-1");
            final RefundRequest withReason = original.withReason("customer request");

            assertThat(original.reason()).isNull();
            assertThat(withReason.reason()).isEqualTo("customer request");
            assertThat(withReason.paymentRef()).isEqualTo("payment-1");
        }
    }

    @Nested
    class RefundReceiptTests {

        @Test
        void refusesANullRefundRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RefundReceipt(
                            null, "payment-1", null, false, Instant.parse("2026-01-01T00:00:00Z")))
                    .withMessageContaining("refundRef");
        }

        @Test
        void refusesABlankRefundRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RefundReceipt(
                            "   ", "payment-1", null, false, Instant.parse("2026-01-01T00:00:00Z")))
                    .withMessageContaining("refundRef");
        }

        @Test
        void refusesANullAcceptedAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new RefundReceipt("refund-1", "payment-1", null, false, null))
                    .withMessageContaining("acceptedAt");
        }

        @Test
        void carriesEveryFieldItWasGiven() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final Instant acceptedAt = Instant.parse("2026-01-01T00:00:00Z");

            final RefundReceipt receipt = new RefundReceipt("refund-1", "payment-1", amount, true, acceptedAt);

            assertThat(receipt.refundRef()).isEqualTo("refund-1");
            assertThat(receipt.paymentRef()).isEqualTo("payment-1");
            assertThat(receipt.amount()).isEqualTo(amount);
            assertThat(receipt.pending()).isTrue();
            assertThat(receipt.acceptedAt()).isEqualTo(acceptedAt);
        }
    }
}
