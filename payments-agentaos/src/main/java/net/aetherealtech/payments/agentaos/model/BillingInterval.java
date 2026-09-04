package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

import net.aetherealtech.payments.PeriodUnit;
import net.aetherealtech.payments.Recurrence;

/**
 * How often a subscription link bills. AgentaOS offers these two and nothing else.
 *
 * <p>That is why a {@link Recurrence} cannot be turned into a subscription here on its own: a cadence
 * that is not exactly one month or exactly one year has nowhere to go, and quietly rounding it would
 * bill somebody on a schedule they did not agree to.
 */
public enum BillingInterval {

    /** Every month. */
    MONTH,

    /** Every year. */
    YEAR;

    /** The wire spelling. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The constant for a wire value, or null when absent or unrecognised. */
    public static BillingInterval fromWire(final String value) {
        if (value == null) {
            return null;
        }
        final String normalised = value.toLowerCase(Locale.ROOT);
        for (final BillingInterval interval : values()) {
            if (interval.wireValue().equals(normalised)) {
                return interval;
            }
        }
        return null;
    }

    /**
     * The interval a {@link Recurrence} asks for, or null when AgentaOS cannot express it.
     *
     * <p>Only a period of exactly one month or one year maps. Everything else — a fortnight, a quarter,
     * two years — answers null, and the caller refuses by name rather than substituting the nearest.
     */
    public static BillingInterval from(final Recurrence recurrence) {
        if (recurrence == null || recurrence.periodLength() != 1) {
            return null;
        }
        if (recurrence.unit() == PeriodUnit.MONTH) {
            return MONTH;
        }
        return recurrence.unit() == PeriodUnit.YEAR ? YEAR : null;
    }
}
