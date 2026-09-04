package net.aetherealtech.payments;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount and the currency it is denominated in.
 *
 * <p>{@code amount} is a {@link BigDecimal} and {@code currency} an ISO 4217 alphabetic code, at every
 * point of this SPI, because the providers behind it disagree with each other and with themselves.
 * Bankart takes decimal strings; AgentaOS mixes decimal numbers (checkouts, payment links) with integer
 * minor units (subscriptions, invoices) across its own resources. Converting is an adapter's job, done
 * once where the resource is known, rather than a caller's job done differently at every call site —
 * the arithmetic that turns 1999 into 19.99 has to know the currency's exponent, and a consumer holding
 * a bare {@code long} does not.
 *
 * <p>No arithmetic lives here. Adding two amounts, prorating one, or converting between currencies are
 * all decisions with a product's consequences (which rate, rounded which way, at which moment), and a
 * library that offered a convenient {@code plus} would be making them silently.
 *
 * @param amount   the amount, never null; may be zero or negative — a refund and a credit are both real
 * @param currency an ISO 4217 alphabetic code in upper case, e.g. {@code EUR}, {@code MKD}
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (!isIso4217Alphabetic(currency)) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO 4217 code in upper case, got \"" + currency + "\"");
        }
    }

    /**
     * The amount as an exact decimal string — {@code "9.99"}, never {@code "9.99E+0"}.
     *
     * <p>{@code toPlainString}, because {@code new BigDecimal("9.99E+2")} is a legitimate way to hold 999
     * and its {@code toString} carries the exponent through to whatever wire is listening.
     */
    public static Money of(final String amount, final String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /**
     * The amount expressed in minor units, e.g. {@code 1999} with {@code exponent} 2 becomes 19.99.
     *
     * <p>The exponent is a parameter rather than looked up from the currency because a provider states
     * which exponent ITS integers use, and that is what has to be undone. Guessing from a currency table
     * would be right until the first three-decimal currency or the first provider that scales EUR by
     * 10 000 for micro-payments.
     */
    public static Money ofMinorUnits(final long minorUnits, final int exponent, final String currency) {
        if (exponent < 0) {
            throw new IllegalArgumentException("exponent must not be negative, got " + exponent);
        }
        return new Money(BigDecimal.valueOf(minorUnits, exponent), currency);
    }

    /** The amount as a plain decimal string, which is what every provider modelled here transmits. */
    public String amountAsString() {
        return amount.toPlainString();
    }

    private static boolean isIso4217Alphabetic(final String currency) {
        if (currency.length() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            final char c = currency.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return true;
    }
}
