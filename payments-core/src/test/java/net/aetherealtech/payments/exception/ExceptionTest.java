package net.aetherealtech.payments.exception;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.ProviderCapability;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Nested
    class PaymentExceptionTests {

        @Test
        void carriesAMessageAlone() {
            final PaymentException exception = new PaymentException("something went wrong");
            assertThat(exception.getMessage()).isEqualTo("something went wrong");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        void carriesAMessageAndACause() {
            final Throwable cause = new RuntimeException("network reset");
            final PaymentException exception = new PaymentException("something went wrong", cause);
            assertThat(exception.getMessage()).isEqualTo("something went wrong");
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }

    @Nested
    class PaymentDeclinedExceptionTests {

        @Test
        void carriesTheProviderAndDeclineCode() {
            final PaymentDeclinedException exception =
                    new PaymentDeclinedException("card declined", "bankart", "insufficient_funds");

            assertThat(exception).isInstanceOf(PaymentException.class);
            assertThat(exception.getMessage()).isEqualTo("card declined");
            assertThat(exception.provider()).isEqualTo("bankart");
            assertThat(exception.declineCode()).isEqualTo("insufficient_funds");
        }
    }

    @Nested
    class PaymentProviderExceptionTests {

        @Test
        void theFourArgConstructorLeavesOutcomeUnknownFalse() {
            final PaymentProviderException exception =
                    new PaymentProviderException("request rejected", "bankart", "invalid_request", null);

            assertThat(exception).isInstanceOf(PaymentException.class);
            assertThat(exception.provider()).isEqualTo("bankart");
            assertThat(exception.code()).isEqualTo("invalid_request");
            assertThat(exception.outcomeUnknown()).isFalse();
        }

        @Test
        void theFiveArgConstructorHonoursOutcomeUnknown() {
            final Throwable cause = new RuntimeException("timeout");
            final PaymentProviderException exception =
                    new PaymentProviderException("call timed out", "bankart", null, true, cause);

            assertThat(exception.outcomeUnknown()).isTrue();
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.code()).isNull();
        }
    }

    @Nested
    class UnsupportedCapabilityExceptionTests {

        @Test
        void theMessageNamesTheProviderAndTheCapability() {
            final UnsupportedCapabilityException exception =
                    new UnsupportedCapabilityException("bankart", ProviderCapability.REFUND);

            assertThat(exception).isInstanceOf(PaymentException.class);
            assertThat(exception.getMessage()).contains("bankart").contains("REFUND");
            assertThat(exception.provider()).isEqualTo("bankart");
            assertThat(exception.capability()).isEqualTo(ProviderCapability.REFUND);
        }
    }

    @Nested
    class WebhookVerificationExceptionTests {

        @Test
        void carriesTheProviderAndTheFailedComponent() {
            final WebhookVerificationException exception = new WebhookVerificationException(
                    "signature did not match", "bankart", "signature");

            assertThat(exception).isInstanceOf(PaymentException.class);
            assertThat(exception.getMessage()).isEqualTo("signature did not match");
            assertThat(exception.provider()).isEqualTo("bankart");
            assertThat(exception.reason()).isEqualTo("signature");
        }
    }
}
