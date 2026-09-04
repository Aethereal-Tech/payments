package net.aetherealtech.payments.testing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundReceipt;
import net.aetherealtech.payments.RefundRequest;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.event.EventHeader;
import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.event.UnknownEvent;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RecordingPaymentProviderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static PaymentIntent oneOffIntent(final String merchantReference) {
        return PaymentIntent.builder(merchantReference)
                .amount(new Money(BigDecimal.TEN, "EUR"))
                .build();
    }

    private static PaymentIntent recurringIntent(final String merchantReference) {
        return PaymentIntent.builder(merchantReference)
                .planRef("plan-1")
                .build();
    }

    private static RedirectTarget redirectTarget(final String checkoutRef) {
        return RedirectTarget.of("https://pay.example/" + checkoutRef, checkoutRef);
    }

    private static SubscriptionSnapshot snapshot(final String subscriptionRef) {
        return SubscriptionSnapshot.builder(subscriptionRef, SubscriptionStatus.ACTIVE, NOW).build();
    }

    private static InboundWebhook webhook() {
        return InboundWebhook.post(new byte[0], "/webhook", Map.of());
    }

    private static PaymentEvent event(final String eventId) {
        return UnknownEvent.of(EventHeader.builder(eventId, "recording", NOW).build(), "invoice.paid");
    }

    private static RefundReceipt refundReceipt(final String refundRef) {
        return new RefundReceipt(refundRef, "payment-1", null, false, NOW);
    }

    @Nested
    class ScriptingTests {

        @Test
        void scriptedResultsAreReturnedInFifoOrder() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"))
                    .willReturnCheckout(redirectTarget("checkout-2"));

            assertThat(provider.startCheckout(oneOffIntent("order-1")).checkoutRef()).isEqualTo("checkout-1");
            assertThat(provider.startCheckout(oneOffIntent("order-2")).checkoutRef()).isEqualTo("checkout-2");
        }

        @Test
        void anUnscriptedStartCheckoutNamesTheMethodAndTheScriptCall() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.startCheckout(oneOffIntent("order-1")))
                    .withMessageContaining("startCheckout")
                    .withMessageContaining("willReturnCheckout");
        }

        @Test
        void anUnscriptedHandleWebhookNamesTheMethodAndTheScriptCall() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.handleWebhook(webhook()))
                    .withMessageContaining("handleWebhook")
                    .withMessageContaining("willReturnEvent");
        }

        @Test
        void anUnscriptedReconcileNamesTheMethodAndTheScriptCall() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.reconcile("sub-1"))
                    .withMessageContaining("reconcile")
                    .withMessageContaining("willReturnSnapshot");
        }

        @Test
        void anUnscriptedCancelSubscriptionNamesTheMethodAndTheScriptCall() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.cancelSubscription("sub-1", false))
                    .withMessageContaining("cancelSubscription")
                    .withMessageContaining("willReturnCancellation");
        }

        @Test
        void anUnscriptedRefundNamesTheMethodAndTheScriptCall() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.refund(RefundRequest.full("payment-1")))
                    .withMessageContaining("refund")
                    .withMessageContaining("willReturnRefund");
        }

        @Test
        void aScriptedRuntimeExceptionIsThrownRatherThanReturned() {
            final RuntimeException failure = new IllegalStateException("gateway down");
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willFailCheckout(failure);

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> provider.startCheckout(oneOffIntent("order-1")))
                    .isSameAs(failure);
        }

        @Test
        void aScriptedFailureAndAScriptedResultShareOneFifoQueue() {
            final RuntimeException failure = new IllegalStateException("declined");
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willFailRefund(failure)
                    .willReturnRefund(refundReceipt("refund-1"));

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> provider.refund(RefundRequest.full("payment-1")))
                    .isSameAs(failure);
            assertThat(provider.refund(RefundRequest.full("payment-1")).refundRef()).isEqualTo("refund-1");
        }
    }

    @Nested
    class CallLogTests {

        @Test
        void callsAreRecordedInOrder() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"))
                    .willReturnCheckout(redirectTarget("checkout-2"));

            provider.startCheckout(oneOffIntent("order-1"));
            provider.startCheckout(oneOffIntent("order-2"));

            assertThat(provider.checkouts())
                    .extracting(PaymentIntent::merchantReference)
                    .containsExactly("order-1", "order-2");
        }

        @Test
        void lastCheckoutReturnsTheMostRecentOne() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"))
                    .willReturnCheckout(redirectTarget("checkout-2"));

            provider.startCheckout(oneOffIntent("order-1"));
            provider.startCheckout(oneOffIntent("order-2"));

            assertThat(provider.lastCheckout().merchantReference()).isEqualTo("order-2");
        }

        @Test
        void lastRefundReturnsTheMostRecentOne() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnRefund(refundReceipt("refund-1"))
                    .willReturnRefund(refundReceipt("refund-2"));

            provider.refund(RefundRequest.full("payment-1"));
            provider.refund(RefundRequest.full("payment-2"));

            assertThat(provider.lastRefund().paymentRef()).isEqualTo("payment-2");
        }

        @Test
        void lastCheckoutOnAFreshDoubleThrowsRatherThanReturningNull() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(provider::lastCheckout)
                    .withMessageContaining("startCheckout");
        }

        @Test
        void lastRefundOnAFreshDoubleThrowsRatherThanReturningNull() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThatIllegalStateException()
                    .isThrownBy(provider::lastRefund)
                    .withMessageContaining("refund");
        }

        @Test
        void resetCallsClearsTheLogButKeepsTheScript() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"))
                    .willReturnCheckout(redirectTarget("checkout-2"));

            provider.startCheckout(oneOffIntent("order-1"));
            provider.resetCalls();

            assertThat(provider.checkouts()).isEmpty();
            assertThat(provider.scriptExhausted()).isFalse();
            assertThat(provider.startCheckout(oneOffIntent("order-2")).checkoutRef()).isEqualTo("checkout-2");
        }

        @Test
        void scriptExhaustedIsTrueWithNothingQueued() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();
            assertThat(provider.scriptExhausted()).isTrue();
        }

        @Test
        void scriptExhaustedFlipsOnceTheQueuedAnswerIsHandedOut() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"));

            assertThat(provider.scriptExhausted()).isFalse();
            provider.startCheckout(oneOffIntent("order-1"));
            assertThat(provider.scriptExhausted()).isTrue();
        }

        @Test
        void cancellationsRecordTheSubscriptionRefAndTheAtPeriodEndFlag() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCancellation(snapshot("sub-1"));

            provider.cancelSubscription("sub-1", true);

            assertThat(provider.cancellations())
                    .containsExactly(new RecordingPaymentProvider.Cancellation("sub-1", true));
        }

        @Test
        void everyRecordedCallListIsAnImmutableCopy() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability()
                    .willReturnCheckout(redirectTarget("checkout-1"))
                    .willReturnEvent(event("evt-1"))
                    .willReturnSnapshot(snapshot("sub-1"))
                    .willReturnCancellation(snapshot("sub-1"))
                    .willReturnRefund(refundReceipt("refund-1"));

            provider.startCheckout(oneOffIntent("order-1"));
            provider.handleWebhook(webhook());
            provider.reconcile("sub-1");
            provider.cancelSubscription("sub-1", false);
            provider.refund(RefundRequest.full("payment-1"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> provider.checkouts().add(oneOffIntent("order-2")));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> provider.webhooks().add(webhook()));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> provider.reconciles().add("sub-2"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> provider.cancellations()
                            .add(new RecordingPaymentProvider.Cancellation("sub-2", false)));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> provider.refunds().add(RefundRequest.full("payment-2")));
        }
    }

    @Nested
    class CapabilityGatingTests {

        @Test
        void refundWithoutTheRefundCapabilityRefuses() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.REFUND)
                    .build();

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.refund(RefundRequest.full("payment-1")))
                    .satisfies(exception -> assertThat(exception.capability()).isEqualTo(ProviderCapability.REFUND));
        }

        @Test
        void startCheckoutWithoutHostedCheckoutRefuses() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.HOSTED_CHECKOUT)
                    .build();

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.startCheckout(oneOffIntent("order-1")));
        }

        @Test
        void startCheckoutWithoutRecurringCheckoutRefusesOnlyARecurringIntent() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.RECURRING_CHECKOUT)
                    .build()
                    .willReturnCheckout(redirectTarget("checkout-1"));

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.startCheckout(recurringIntent("order-1")))
                    .satisfies(exception ->
                            assertThat(exception.capability()).isEqualTo(ProviderCapability.RECURRING_CHECKOUT));

            assertThat(provider.startCheckout(oneOffIntent("order-2")).checkoutRef()).isEqualTo("checkout-1");
        }

        @Test
        void reconcileWithoutReconcileRefuses() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.RECONCILE)
                    .build();

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.reconcile("sub-1"));
        }

        @Test
        void cancelSubscriptionWithoutSubscriptionsRefuses() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.SUBSCRIPTIONS)
                    .build();

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.cancelSubscription("sub-1", false));
        }

        @Test
        void cancelSubscriptionWithoutCancelAtPeriodEndRefusesOnlyWhenDeferred() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.CANCEL_AT_PERIOD_END)
                    .build()
                    .willReturnCancellation(snapshot("sub-1"));

            assertThat(provider.cancelSubscription("sub-1", false)).isEqualTo(snapshot("sub-1"));

            assertThatExceptionOfType(UnsupportedCapabilityException.class)
                    .isThrownBy(() -> provider.cancelSubscription("sub-1", true))
                    .satisfies(exception ->
                            assertThat(exception.capability()).isEqualTo(ProviderCapability.CANCEL_AT_PERIOD_END));
        }
    }

    @Nested
    class BuilderTests {

        @Test
        void withEveryCapabilityDeclaresEveryCapabilityUnderTheDefaultId() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.withEveryCapability();

            assertThat(provider.id()).isEqualTo(RecordingPaymentProvider.DEFAULT_ID);
            assertThat(provider.capabilities()).containsExactlyInAnyOrder(ProviderCapability.values());
        }

        @Test
        void capabilitiesWithNoArgumentsYieldsAnEmptySet() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .capabilities()
                    .build();

            assertThat(provider.capabilities()).isEmpty();
        }

        @Test
        void capabilitiesWithArgumentsYieldsExactlyThoseCapabilities() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .capabilities(ProviderCapability.HOSTED_CHECKOUT, ProviderCapability.REFUND)
                    .build();

            assertThat(provider.capabilities())
                    .containsExactlyInAnyOrder(ProviderCapability.HOSTED_CHECKOUT, ProviderCapability.REFUND);
        }

        @Test
        void idIsReflectedInIdAndInRefusalMessages() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .id("custom-provider")
                    .build();

            assertThat(provider.id()).isEqualTo("custom-provider");
            assertThatIllegalStateException()
                    .isThrownBy(() -> provider.startCheckout(oneOffIntent("order-1")))
                    .withMessageContaining("custom-provider");
        }
    }

    @Nested
    class SupportsDefaultMethodTests {

        @Test
        void supportsIsTrueForADeclaredCapability() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.REFUND)
                    .build();

            assertThat(provider.supports(ProviderCapability.HOSTED_CHECKOUT)).isTrue();
        }

        @Test
        void supportsIsFalseForAnUndeclaredCapability() {
            final RecordingPaymentProvider provider = RecordingPaymentProvider.builder()
                    .without(ProviderCapability.REFUND)
                    .build();

            assertThat(provider.supports(ProviderCapability.REFUND)).isFalse();
        }
    }
}
