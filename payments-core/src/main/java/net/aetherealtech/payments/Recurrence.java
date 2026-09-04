package net.aetherealtech.payments;

import java.time.Instant;
import java.util.Objects;

/**
 * How often a recurring checkout should bill, for a provider that has no plan catalogue to name.
 *
 * <p>The two providers modelled here take opposite routes to the same subscription, which is why
 * {@link PaymentIntent} carries both this and a {@link PaymentIntent#planRef()}:
 *
 * <ul>
 *   <li>Bankart has no plans. A subscription is a schedule attached to a registered instrument, and the
 *       cadence travels on the request as {@code periodLength} + {@code periodUnit}. It needs THIS.</li>
 *   <li>AgentaOS has no cadence on a checkout. A subscription is created by a buyer paying a payment
 *       link that already carries {@code billingInterval}, so the cadence is a property of the plan. It
 *       needs a {@code planRef}, and reads a {@code Recurrence} only far enough to check it agrees with
 *       the link.</li>
 * </ul>
 *
 * <p>An adapter given neither, for a checkout it can only serve as recurring, refuses by name rather
 * than quietly selling a one-off.
 *
 * @param periodLength how many {@code unit}s one billing period lasts; must be positive
 * @param unit         the period's unit
 * @param startAt      when the first period begins, or null for "as soon as the instrument is registered"
 * @param trialDays    a free-trial length in days, or null for no trial
 */
public record Recurrence(int periodLength, PeriodUnit unit, Instant startAt, Integer trialDays) {

    public Recurrence {
        Objects.requireNonNull(unit, "unit must not be null");
        if (periodLength <= 0) {
            throw new IllegalArgumentException("periodLength must be positive, got " + periodLength);
        }
        if (trialDays != null && trialDays <= 0) {
            throw new IllegalArgumentException("trialDays must be positive when set, got " + trialDays);
        }
    }

    public static Recurrence monthly() {
        return new Recurrence(1, PeriodUnit.MONTH, null, null);
    }

    public static Recurrence yearly() {
        return new Recurrence(1, PeriodUnit.YEAR, null, null);
    }

    public static Recurrence every(final int periodLength, final PeriodUnit unit) {
        return new Recurrence(periodLength, unit, null, null);
    }

    public Recurrence startingAt(final Instant startAt) {
        return new Recurrence(periodLength, unit, startAt, trialDays);
    }

    public Recurrence withTrialDays(final int trialDays) {
        return new Recurrence(periodLength, unit, startAt, trialDays);
    }
}
