package net.aetherealtech.payments;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RecurrenceTest {

    @Test
    void refusesANullUnit() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Recurrence(1, null, null, null))
                .withMessageContaining("unit");
    }

    @Test
    void refusesAZeroPeriodLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Recurrence(0, PeriodUnit.MONTH, null, null))
                .withMessageContaining("periodLength");
    }

    @Test
    void refusesANegativePeriodLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Recurrence(-1, PeriodUnit.MONTH, null, null))
                .withMessageContaining("periodLength");
    }

    @Test
    void refusesAZeroTrialDays() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Recurrence(1, PeriodUnit.MONTH, null, 0))
                .withMessageContaining("trialDays");
    }

    @Test
    void refusesANegativeTrialDays() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Recurrence(1, PeriodUnit.MONTH, null, -3))
                .withMessageContaining("trialDays");
    }

    @Test
    void aNullTrialDaysIsLegal() {
        final Recurrence recurrence = new Recurrence(1, PeriodUnit.MONTH, null, null);
        assertThat(recurrence.trialDays()).isNull();
    }

    @Test
    void monthlyIsOnePerMonthWithNoTrialOrStart() {
        final Recurrence recurrence = Recurrence.monthly();
        assertThat(recurrence.periodLength()).isEqualTo(1);
        assertThat(recurrence.unit()).isEqualTo(PeriodUnit.MONTH);
        assertThat(recurrence.startAt()).isNull();
        assertThat(recurrence.trialDays()).isNull();
    }

    @Test
    void yearlyIsOnePerYear() {
        final Recurrence recurrence = Recurrence.yearly();
        assertThat(recurrence.periodLength()).isEqualTo(1);
        assertThat(recurrence.unit()).isEqualTo(PeriodUnit.YEAR);
    }

    @Test
    void everyBuildsAnArbitraryCadence() {
        final Recurrence recurrence = Recurrence.every(2, PeriodUnit.WEEK);
        assertThat(recurrence.periodLength()).isEqualTo(2);
        assertThat(recurrence.unit()).isEqualTo(PeriodUnit.WEEK);
    }

    @Test
    void startingAtReplacesOnlyTheStartDate() {
        final Instant start = Instant.parse("2026-01-01T00:00:00Z");
        final Recurrence recurrence = Recurrence.monthly().withTrialDays(7).startingAt(start);
        assertThat(recurrence.startAt()).isEqualTo(start);
        assertThat(recurrence.trialDays()).isEqualTo(7);
        assertThat(recurrence.periodLength()).isEqualTo(1);
        assertThat(recurrence.unit()).isEqualTo(PeriodUnit.MONTH);
    }

    @Test
    void withTrialDaysReplacesOnlyTheTrialLength() {
        final Recurrence recurrence = Recurrence.every(3, PeriodUnit.DAY).withTrialDays(14);
        assertThat(recurrence.trialDays()).isEqualTo(14);
        assertThat(recurrence.periodLength()).isEqualTo(3);
        assertThat(recurrence.unit()).isEqualTo(PeriodUnit.DAY);
    }
}
