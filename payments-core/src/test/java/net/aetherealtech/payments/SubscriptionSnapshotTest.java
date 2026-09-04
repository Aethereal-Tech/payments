package net.aetherealtech.payments;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SubscriptionSnapshotTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void refusesANullSubscriptionRef() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SubscriptionSnapshot(
                        null, SubscriptionStatus.ACTIVE, null, null, null, false, null, null, null, OBSERVED_AT))
                .withMessageContaining("subscriptionRef");
    }

    @Test
    void refusesABlankSubscriptionRef() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SubscriptionSnapshot(
                        "   ", SubscriptionStatus.ACTIVE, null, null, null, false, null, null, null, OBSERVED_AT))
                .withMessageContaining("subscriptionRef");
    }

    @Test
    void refusesANullStatus() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SubscriptionSnapshot(
                        "sub-1", null, null, null, null, false, null, null, null, OBSERVED_AT))
                .withMessageContaining("status");
    }

    @Test
    void refusesANullObservedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SubscriptionSnapshot(
                        "sub-1", SubscriptionStatus.ACTIVE, null, null, null, false, null, null, null, null))
                .withMessageContaining("observedAt");
    }

    @Test
    void activeUntilIsEmptyWhenNoCurrentPeriodEndWasReported() {
        final SubscriptionSnapshot snapshot = SubscriptionSnapshot
                .builder("sub-1", SubscriptionStatus.INCOMPLETE, OBSERVED_AT)
                .build();

        assertThat(snapshot.activeUntil()).isEmpty();
    }

    @Test
    void activeUntilCarriesTheCurrentPeriodEndWhenReported() {
        final Instant periodEnd = Instant.parse("2026-02-01T00:00:00Z");
        final SubscriptionSnapshot snapshot = SubscriptionSnapshot
                .builder("sub-1", SubscriptionStatus.ACTIVE, OBSERVED_AT)
                .currentPeriodEnd(periodEnd)
                .build();

        assertThat(snapshot.activeUntil()).contains(periodEnd);
    }

    @Test
    void builderSetsEveryField() {
        final Plan plan = Plan.of("plan-1", "Gold");
        final Money amount = new Money(BigDecimal.TEN, "EUR");
        final Instant currentPeriodEnd = Instant.parse("2026-02-01T00:00:00Z");
        final Instant effectiveCancelDate = Instant.parse("2026-02-15T00:00:00Z");
        final Instant trialEndsAt = Instant.parse("2026-01-15T00:00:00Z");

        final SubscriptionSnapshot snapshot = SubscriptionSnapshot
                .builder("sub-1", SubscriptionStatus.ACTIVE, OBSERVED_AT)
                .plan(plan)
                .amount(amount)
                .currentPeriodEnd(currentPeriodEnd)
                .cancelAtPeriodEnd(true)
                .effectiveCancelDate(effectiveCancelDate)
                .trialEndsAt(trialEndsAt)
                .customerRef("cust-1")
                .build();

        assertThat(snapshot.subscriptionRef()).isEqualTo("sub-1");
        assertThat(snapshot.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(snapshot.plan()).isEqualTo(plan);
        assertThat(snapshot.amount()).isEqualTo(amount);
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(currentPeriodEnd);
        assertThat(snapshot.cancelAtPeriodEnd()).isTrue();
        assertThat(snapshot.effectiveCancelDate()).isEqualTo(effectiveCancelDate);
        assertThat(snapshot.trialEndsAt()).isEqualTo(trialEndsAt);
        assertThat(snapshot.customerRef()).isEqualTo("cust-1");
    }
}
