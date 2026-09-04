package net.aetherealtech.payments.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.EffectiveTiming;
import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.SubscriptionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PaymentEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static EventHeader header() {
        return EventHeader.builder("evt-1", "bankart", OCCURRED_AT).build();
    }

    /**
     * Switches over every permitted {@link PaymentEvent} type with no default branch. Deleting a case
     * here, or adding a sixteenth event without adding one, stops this file compiling — which is what
     * a sealed interface over sixteen webhook shapes is for.
     */
    private static String label(final PaymentEvent event) {
        return switch (event) {
            case CheckoutCompleted c -> "checkout-completed";
            case PaymentSucceeded p -> "payment-succeeded";
            case PaymentFailed p -> "payment-failed";
            case SubscriptionCreated s -> "subscription-created";
            case SubscriptionRenewed s -> "subscription-renewed";
            case SubscriptionPlanChanged s -> "subscription-plan-changed";
            case SubscriptionCancelled s -> "subscription-cancelled";
            case SubscriptionExpired s -> "subscription-expired";
            case DunningExhausted d -> "dunning-exhausted";
            case TrialStarted t -> "trial-started";
            case TrialEnding t -> "trial-ending";
            case Refunded r -> "refunded";
            case ChargebackOpened c -> "chargeback-opened";
            case ChargebackReversed c -> "chargeback-reversed";
            case UnknownEvent u -> "unknown-event";
        };
    }

    @Nested
    class EventHeaderTests {

        @Test
        void refusesANullEventId() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EventHeader(null, "bankart", OCCURRED_AT, null, null))
                    .withMessageContaining("eventId");
        }

        @Test
        void refusesABlankEventId() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EventHeader("   ", "bankart", OCCURRED_AT, null, null))
                    .withMessageContaining("eventId");
        }

        @Test
        void refusesANullProvider() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EventHeader("evt-1", null, OCCURRED_AT, null, null))
                    .withMessageContaining("provider");
        }

        @Test
        void refusesABlankProvider() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EventHeader("evt-1", "   ", OCCURRED_AT, null, null))
                    .withMessageContaining("provider");
        }

        @Test
        void refusesANullOccurredAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new EventHeader("evt-1", "bankart", null, null, null))
                    .withMessageContaining("occurredAt");
        }

        @Test
        void acknowledgementDefaultsToEmptyStringWhenNull() {
            final EventHeader header = new EventHeader("evt-1", "bankart", OCCURRED_AT, null, null);
            assertThat(header.acknowledgement()).isEmpty();
        }

        @Test
        void rawPayloadDefaultsToEmptyStringWhenNull() {
            final EventHeader header = new EventHeader("evt-1", "bankart", OCCURRED_AT, null, null);
            assertThat(header.rawPayload()).isEmpty();
        }

        @Test
        void builderSetsEveryField() {
            final EventHeader header = EventHeader.builder("evt-1", "bankart", OCCURRED_AT)
                    .acknowledgement("OK")
                    .rawPayload("{\"type\":\"payment.success\"}")
                    .build();

            assertThat(header.eventId()).isEqualTo("evt-1");
            assertThat(header.provider()).isEqualTo("bankart");
            assertThat(header.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(header.acknowledgement()).isEqualTo("OK");
            assertThat(header.rawPayload()).isEqualTo("{\"type\":\"payment.success\"}");
        }
    }

    @Nested
    class DefaultMethodTests {

        @Test
        void eventIdDelegatesToTheHeader() {
            final PaymentEvent event = CheckoutCompleted.builder(header(), "checkout-1").build();
            assertThat(event.eventId()).isEqualTo(header().eventId());
        }

        @Test
        void occurredAtDelegatesToTheHeader() {
            final PaymentEvent event = CheckoutCompleted.builder(header(), "checkout-1").build();
            assertThat(event.occurredAt()).isEqualTo(header().occurredAt());
        }

        @Test
        void acknowledgementDelegatesToTheHeader() {
            final EventHeader ackHeader = EventHeader.builder("evt-1", "bankart", OCCURRED_AT)
                    .acknowledgement("OK")
                    .build();
            final PaymentEvent event = CheckoutCompleted.builder(ackHeader, "checkout-1").build();

            assertThat(event.acknowledgement()).isEqualTo("OK");
        }
    }

    @Nested
    class ExhaustiveSwitchTests {

        @Test
        void aSwitchWithNoDefaultLabelsEveryPermittedEventType() {
            assertThat(label(CheckoutCompleted.builder(header(), "checkout-1").build()))
                    .isEqualTo("checkout-completed");
            assertThat(label(PaymentSucceeded.builder(header(), "payment-1").build()))
                    .isEqualTo("payment-succeeded");
            assertThat(label(PaymentFailed.builder(header()).build()))
                    .isEqualTo("payment-failed");
            assertThat(label(SubscriptionCreated.builder(header(), "sub-1", SubscriptionStatus.ACTIVE).build()))
                    .isEqualTo("subscription-created");
            assertThat(label(SubscriptionRenewed.builder(header(), "sub-1", SubscriptionStatus.ACTIVE, OCCURRED_AT).build()))
                    .isEqualTo("subscription-renewed");
            assertThat(label(SubscriptionPlanChanged.builder(header(), "sub-1", SubscriptionStatus.ACTIVE)
                    .from(Plan.of("old"))
                    .to(Plan.of("new"))
                    .effective(EffectiveTiming.IMMEDIATE, OCCURRED_AT)
                    .build()))
                    .isEqualTo("subscription-plan-changed");
            assertThat(label(SubscriptionCancelled.builder(
                    header(), "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, OCCURRED_AT).build()))
                    .isEqualTo("subscription-cancelled");
            assertThat(label(SubscriptionExpired.builder(header(), "sub-1", OCCURRED_AT).build()))
                    .isEqualTo("subscription-expired");
            assertThat(label(DunningExhausted.builder(header(), "sub-1", SubscriptionStatus.EXPIRED).build()))
                    .isEqualTo("dunning-exhausted");
            assertThat(label(TrialStarted.builder(header(), "sub-1", OCCURRED_AT).build()))
                    .isEqualTo("trial-started");
            assertThat(label(TrialEnding.builder(header(), "sub-1", OCCURRED_AT).build()))
                    .isEqualTo("trial-ending");
            assertThat(label(Refunded.builder(header(), "refund-1").build()))
                    .isEqualTo("refunded");
            assertThat(label(ChargebackOpened.builder(header(), "payment-1").build()))
                    .isEqualTo("chargeback-opened");
            assertThat(label(ChargebackReversed.builder(header(), "payment-1").build()))
                    .isEqualTo("chargeback-reversed");
            assertThat(label(UnknownEvent.of(header(), "invoice.paid")))
                    .isEqualTo("unknown-event");
        }
    }

    @Nested
    class CheckoutCompletedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new CheckoutCompleted(null, "checkout-1", null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void metadataDefaultsToAnEmptyMapWhenNull() {
            final CheckoutCompleted event = new CheckoutCompleted(header(), "checkout-1", null, null, null, null);
            assertThat(event.metadata()).isNotNull().isEmpty();
        }

        @Test
        void metadataIsCopiedRatherThanAliased() {
            final Map<String, String> source = new HashMap<>();
            source.put("orderSource", "web");
            final CheckoutCompleted event = new CheckoutCompleted(header(), "checkout-1", null, null, null, source);

            source.put("orderSource", "mobile");

            assertThat(event.metadata()).containsExactly(Map.entry("orderSource", "web"));
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final Map<String, String> metadata = Map.of("orderSource", "web");

            final CheckoutCompleted event = CheckoutCompleted.builder(header(), "checkout-1")
                    .merchantReference("order-1")
                    .amount(amount)
                    .paymentRef("payment-1")
                    .metadata(metadata)
                    .build();

            assertThat(event.header()).isEqualTo(header());
            assertThat(event.checkoutRef()).isEqualTo("checkout-1");
            assertThat(event.merchantReference()).isEqualTo("order-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.metadata()).isEqualTo(metadata);
        }
    }

    @Nested
    class PaymentSucceededTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PaymentSucceeded(null, "payment-1", null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PaymentSucceeded(header(), null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void refusesABlankPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PaymentSucceeded(header(), "   ", null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void subscriptionIsEmptyForAOneOffCharge() {
            final PaymentSucceeded event = PaymentSucceeded.builder(header(), "payment-1").build();
            assertThat(event.subscription()).isEmpty();
        }

        @Test
        void subscriptionIsPresentForARenewalCycle() {
            final PaymentSucceeded event = PaymentSucceeded.builder(header(), "payment-1")
                    .subscriptionRef("sub-1")
                    .build();
            assertThat(event.subscription()).contains("sub-1");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final PaymentSucceeded event = PaymentSucceeded.builder(header(), "payment-1")
                    .merchantReference("order-1")
                    .amount(amount)
                    .subscriptionRef("sub-1")
                    .build();

            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.merchantReference()).isEqualTo("order-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
        }
    }

    @Nested
    class PaymentFailedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PaymentFailed(null, null, null, null, null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void subscriptionIsEmptyForAOneOffCharge() {
            final PaymentFailed event = PaymentFailed.builder(header()).build();
            assertThat(event.subscription()).isEmpty();
        }

        @Test
        void subscriptionIsPresentWhenThisFailureBelongsToACycle() {
            final PaymentFailed event = PaymentFailed.builder(header()).subscriptionRef("sub-1").build();
            assertThat(event.subscription()).contains("sub-1");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final PaymentFailed event = PaymentFailed.builder(header())
                    .paymentRef("payment-1")
                    .merchantReference("order-1")
                    .amount(amount)
                    .subscriptionRef("sub-1")
                    .subscriptionStatus(SubscriptionStatus.PAST_DUE)
                    .declineCode("insufficient_funds")
                    .reason("Card declined")
                    .build();

            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.merchantReference()).isEqualTo("order-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.subscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(event.declineCode()).isEqualTo("insufficient_funds");
            assertThat(event.reason()).isEqualTo("Card declined");
        }
    }

    @Nested
    class SubscriptionCreatedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCreated(
                            null, "sub-1", SubscriptionStatus.ACTIVE, null, null, null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCreated(
                            header(), "sub-1", null, null, null, null, null, null, null))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionCreated(
                            header(), null, SubscriptionStatus.ACTIVE, null, null, null, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionCreated(
                            header(), "   ", SubscriptionStatus.ACTIVE, null, null, null, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final Plan plan = Plan.of("plan-1", "Gold");
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final Instant currentPeriodEnd = Instant.parse("2026-02-01T00:00:00Z");
            final Instant trialEndsAt = Instant.parse("2026-01-15T00:00:00Z");

            final SubscriptionCreated event = SubscriptionCreated
                    .builder(header(), "sub-1", SubscriptionStatus.TRIALING)
                    .plan(plan)
                    .amount(amount)
                    .currentPeriodEnd(currentPeriodEnd)
                    .trialEndsAt(trialEndsAt)
                    .customerRef("cust-1")
                    .merchantReference("order-1")
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.status()).isEqualTo(SubscriptionStatus.TRIALING);
            assertThat(event.plan()).isEqualTo(plan);
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.currentPeriodEnd()).isEqualTo(currentPeriodEnd);
            assertThat(event.trialEndsAt()).isEqualTo(trialEndsAt);
            assertThat(event.customerRef()).isEqualTo("cust-1");
            assertThat(event.merchantReference()).isEqualTo("order-1");
        }
    }

    @Nested
    class SubscriptionRenewedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionRenewed(
                            null, "sub-1", SubscriptionStatus.ACTIVE, OCCURRED_AT, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionRenewed(
                            header(), "sub-1", null, OCCURRED_AT, null, null, null))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullCurrentPeriodEnd() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionRenewed(
                            header(), "sub-1", SubscriptionStatus.ACTIVE, null, null, null, null))
                    .withMessageContaining("currentPeriodEnd");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionRenewed(
                            header(), null, SubscriptionStatus.ACTIVE, OCCURRED_AT, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionRenewed(
                            header(), "   ", SubscriptionStatus.ACTIVE, OCCURRED_AT, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final Plan plan = Plan.of("plan-1");
            final Money amount = new Money(BigDecimal.TEN, "EUR");

            final SubscriptionRenewed event = SubscriptionRenewed
                    .builder(header(), "sub-1", SubscriptionStatus.ACTIVE, OCCURRED_AT)
                    .plan(plan)
                    .amount(amount)
                    .paymentRef("payment-1")
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(event.currentPeriodEnd()).isEqualTo(OCCURRED_AT);
            assertThat(event.plan()).isEqualTo(plan);
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.paymentRef()).isEqualTo("payment-1");
        }
    }

    @Nested
    class SubscriptionPlanChangedTests {

        private final Plan previousPlan = Plan.of("basic");
        private final Plan newPlan = Plan.of("gold");

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            null, "sub-1", SubscriptionStatus.ACTIVE, previousPlan, newPlan,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "sub-1", null, previousPlan, newPlan,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullPreviousPlan() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "sub-1", SubscriptionStatus.ACTIVE, null, newPlan,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("previousPlan");
        }

        @Test
        void refusesANullNewPlan() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "sub-1", SubscriptionStatus.ACTIVE, previousPlan, null,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("newPlan");
        }

        @Test
        void refusesANullTiming() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "sub-1", SubscriptionStatus.ACTIVE, previousPlan, newPlan,
                            null, OCCURRED_AT))
                    .withMessageContaining("timing");
        }

        @Test
        void refusesANullEffectiveAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "sub-1", SubscriptionStatus.ACTIVE, previousPlan, newPlan,
                            EffectiveTiming.IMMEDIATE, null))
                    .withMessageContaining("effectiveAt");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), null, SubscriptionStatus.ACTIVE, previousPlan, newPlan,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionPlanChanged(
                            header(), "   ", SubscriptionStatus.ACTIVE, previousPlan, newPlan,
                            EffectiveTiming.IMMEDIATE, OCCURRED_AT))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final SubscriptionPlanChanged event = SubscriptionPlanChanged
                    .builder(header(), "sub-1", SubscriptionStatus.ACTIVE)
                    .from(previousPlan)
                    .to(newPlan)
                    .effective(EffectiveTiming.AT_PERIOD_END, OCCURRED_AT)
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(event.previousPlan()).isEqualTo(previousPlan);
            assertThat(event.newPlan()).isEqualTo(newPlan);
            assertThat(event.timing()).isEqualTo(EffectiveTiming.AT_PERIOD_END);
            assertThat(event.effectiveAt()).isEqualTo(OCCURRED_AT);
        }
    }

    @Nested
    class SubscriptionCancelledTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            null, "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, OCCURRED_AT, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            header(), "sub-1", null, EffectiveTiming.IMMEDIATE, OCCURRED_AT, null))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullTiming() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            header(), "sub-1", SubscriptionStatus.CANCELLED, null, OCCURRED_AT, null))
                    .withMessageContaining("timing");
        }

        @Test
        void refusesANullEffectiveAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            header(), "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, null, null))
                    .withMessageContaining("effectiveAt");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            header(), null, SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionCancelled(
                            header(), "   ", SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void endsAtPeriodEndIsTrueOnlyWhenTimingIsAtPeriodEnd() {
            final SubscriptionCancelled event = SubscriptionCancelled.builder(
                    header(), "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.AT_PERIOD_END, OCCURRED_AT).build();
            assertThat(event.endsAtPeriodEnd()).isTrue();
        }

        @Test
        void endsAtPeriodEndIsFalseWhenCancellationIsImmediate() {
            final SubscriptionCancelled event = SubscriptionCancelled.builder(
                    header(), "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.IMMEDIATE, OCCURRED_AT).build();
            assertThat(event.endsAtPeriodEnd()).isFalse();
        }

        @Test
        void builderSetsEveryField() {
            final SubscriptionCancelled event = SubscriptionCancelled.builder(
                            header(), "sub-1", SubscriptionStatus.CANCELLED, EffectiveTiming.AT_PERIOD_END, OCCURRED_AT)
                    .reason("subscriber requested")
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.status()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(event.timing()).isEqualTo(EffectiveTiming.AT_PERIOD_END);
            assertThat(event.effectiveAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.reason()).isEqualTo("subscriber requested");
        }
    }

    @Nested
    class SubscriptionExpiredTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionExpired(null, "sub-1", SubscriptionStatus.EXPIRED, OCCURRED_AT))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionExpired(header(), "sub-1", null, OCCURRED_AT))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullExpiredAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SubscriptionExpired(header(), "sub-1", SubscriptionStatus.EXPIRED, null))
                    .withMessageContaining("expiredAt");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionExpired(header(), null, SubscriptionStatus.EXPIRED, OCCURRED_AT))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SubscriptionExpired(header(), "   ", SubscriptionStatus.EXPIRED, OCCURRED_AT))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderDefaultsStatusToExpired() {
            final SubscriptionExpired event = SubscriptionExpired.builder(header(), "sub-1", OCCURRED_AT).build();
            assertThat(event.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        }

        @Test
        void builderStatusOverridesTheDefault() {
            final SubscriptionExpired event = SubscriptionExpired.builder(header(), "sub-1", OCCURRED_AT)
                    .status(SubscriptionStatus.CANCELLED)
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.expiredAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        }
    }

    @Nested
    class DunningExhaustedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DunningExhausted(
                            null, "sub-1", SubscriptionStatus.EXPIRED, null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullStatus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DunningExhausted(header(), "sub-1", null, null, null, null, null))
                    .withMessageContaining("status");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DunningExhausted(
                            header(), null, SubscriptionStatus.EXPIRED, null, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DunningExhausted(
                            header(), "   ", SubscriptionStatus.EXPIRED, null, null, null, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final DunningExhausted event = DunningExhausted.builder(header(), "sub-1", SubscriptionStatus.EXPIRED)
                    .amount(amount)
                    .attempts(4)
                    .lastPaymentRef("payment-4")
                    .reason("retries exhausted")
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.status()).isEqualTo(SubscriptionStatus.EXPIRED);
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.attempts()).isEqualTo(4);
            assertThat(event.lastPaymentRef()).isEqualTo("payment-4");
            assertThat(event.reason()).isEqualTo("retries exhausted");
        }
    }

    @Nested
    class TrialStartedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new TrialStarted(null, "sub-1", OCCURRED_AT, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullTrialEndsAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new TrialStarted(header(), "sub-1", null, null))
                    .withMessageContaining("trialEndsAt");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new TrialStarted(header(), null, OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new TrialStarted(header(), "   ", OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final Plan plan = Plan.of("plan-1");
            final TrialStarted event = TrialStarted.builder(header(), "sub-1", OCCURRED_AT)
                    .plan(plan)
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.trialEndsAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.plan()).isEqualTo(plan);
        }
    }

    @Nested
    class TrialEndingTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new TrialEnding(null, "sub-1", OCCURRED_AT, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullTrialEndsAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new TrialEnding(header(), "sub-1", null, null))
                    .withMessageContaining("trialEndsAt");
        }

        @Test
        void refusesANullSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new TrialEnding(header(), null, OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void refusesABlankSubscriptionRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new TrialEnding(header(), "   ", OCCURRED_AT, null))
                    .withMessageContaining("subscriptionRef");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final TrialEnding event = TrialEnding.builder(header(), "sub-1", OCCURRED_AT)
                    .amount(amount)
                    .build();

            assertThat(event.subscriptionRef()).isEqualTo("sub-1");
            assertThat(event.trialEndsAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.amount()).isEqualTo(amount);
        }
    }

    @Nested
    class RefundedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Refunded(null, "refund-1", null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullRefundRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Refunded(header(), null, null, null, null, null))
                    .withMessageContaining("refundRef");
        }

        @Test
        void refusesABlankRefundRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Refunded(header(), "   ", null, null, null, null))
                    .withMessageContaining("refundRef");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final Refunded event = Refunded.builder(header(), "refund-1")
                    .paymentRef("payment-1")
                    .amount(amount)
                    .merchantReference("order-1")
                    .reason("customer request")
                    .build();

            assertThat(event.refundRef()).isEqualTo("refund-1");
            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.merchantReference()).isEqualTo("order-1");
            assertThat(event.reason()).isEqualTo("customer request");
        }
    }

    @Nested
    class ChargebackOpenedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ChargebackOpened(null, "payment-1", null, null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ChargebackOpened(header(), null, null, null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void refusesABlankPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ChargebackOpened(header(), "   ", null, null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final ChargebackOpened event = ChargebackOpened.builder(header(), "payment-1")
                    .chargebackRef("chargeback-1")
                    .amount(amount)
                    .reasonCode("10.4")
                    .reason("fraud")
                    .merchantReference("order-1")
                    .build();

            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.chargebackRef()).isEqualTo("chargeback-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.reasonCode()).isEqualTo("10.4");
            assertThat(event.reason()).isEqualTo("fraud");
            assertThat(event.merchantReference()).isEqualTo("order-1");
        }
    }

    @Nested
    class ChargebackReversedTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ChargebackReversed(null, "payment-1", null, null, null, null))
                    .withMessageContaining("header");
        }

        @Test
        void refusesANullPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ChargebackReversed(header(), null, null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void refusesABlankPaymentRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ChargebackReversed(header(), "   ", null, null, null, null))
                    .withMessageContaining("paymentRef");
        }

        @Test
        void builderSetsEveryField() {
            final Money amount = new Money(BigDecimal.TEN, "EUR");
            final ChargebackReversed event = ChargebackReversed.builder(header(), "payment-1")
                    .chargebackRef("chargeback-1")
                    .amount(amount)
                    .reason("dispute won")
                    .merchantReference("order-1")
                    .build();

            assertThat(event.paymentRef()).isEqualTo("payment-1");
            assertThat(event.chargebackRef()).isEqualTo("chargeback-1");
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.reason()).isEqualTo("dispute won");
            assertThat(event.merchantReference()).isEqualTo("order-1");
        }
    }

    @Nested
    class UnknownEventTests {

        @Test
        void refusesANullHeader() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new UnknownEvent(null, "invoice.paid"))
                    .withMessageContaining("header");
        }

        @Test
        void ofFactoryCarriesTheProviderEventType() {
            final UnknownEvent event = UnknownEvent.of(header(), "invoice.paid");
            assertThat(event.header()).isEqualTo(header());
            assertThat(event.providerEventType()).isEqualTo("invoice.paid");
        }

        @Test
        void providerEventTypeMayBeNull() {
            final UnknownEvent event = UnknownEvent.of(header(), null);
            assertThat(event.providerEventType()).isNull();
        }
    }
}
