package net.aetherealtech.payments.agentaos.internal;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.exception.PaymentProviderException;

/**
 * NOT API. The one place AgentaOS's two amount conventions are undone.
 *
 * <p>The gateway uses both, resource by resource. Checkouts, payment links, transactions and invoices
 * carry a DECIMAL in major units — {@code 29.99}. Subscriptions and plan changes carry an INTEGER in
 * minor units — {@code 1999} for €19.99. Webhook checkout and send payloads carry the major-unit figure
 * as a STRING. Getting one of those wrong is a hundredfold error in a price, in either direction, so the
 * conversion happens here, once per resource, where the convention is known — and never at a call site
 * holding a bare number with no idea which kind it is.
 */
public final class Amounts {

    /**
     * The exponent for the two currencies AgentaOS names, and the reason an unnamed one is refused
     * rather than scaled.
     */
    private static final int MINOR_UNIT_EXPONENT = 2;

    private static final Set<String> KNOWN_EXPONENT_CURRENCIES = Set.of("EUR", "USD");

    private Amounts() {
    }

    /** A major-unit decimal and its currency, or null when either is absent or unusable. */
    public static Money fromMajorUnits(final BigDecimal amount, final String currency) {
        final String code = normalise(currency);
        if (amount == null || code == null) {
            return null;
        }
        return new Money(amount, code);
    }

    /** The same from the string form the webhook payloads use. Null when absent or not a number. */
    public static Money fromMajorUnits(final String amount, final String currency) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return fromMajorUnits(new BigDecimal(amount.trim()), currency);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * An integer minor-unit amount, refusing a currency whose exponent AgentaOS has not stated.
     *
     * <p>Two is right for EUR and USD, which are the only currencies the SDK names. It is wrong for JPY,
     * where there are no minor units at all, and wrong for the three-decimal currencies — and AgentaOS's
     * subscriptions are Stripe-backed ({@code stripeSubscriptionId} is on the record), where exactly
     * those exponents are in use. Scaling one of them by 100 would misstate a price by a factor of ten
     * or a hundred and no test downstream would catch it, so this refuses instead.
     *
     * @throws PaymentProviderException when the currency's exponent is not known
     */
    @Provisional("types.ts documents unitAmountMinor as \"Per-cycle amount in integer minor units "
            + "(e.g. 1999 = EUR 19.99)\" and names only EUR and USD as currencies; the exponent for any "
            + "other currency is stated nowhere in packages/pay/src.")
    public static Money fromMinorUnits(final Long minorUnits, final String currency, final String provider) {
        final String code = normalise(currency);
        if (minorUnits == null || code == null) {
            return null;
        }
        if (!KNOWN_EXPONENT_CURRENCIES.contains(code)) {
            throw new PaymentProviderException(
                    "AgentaOS reported " + minorUnits + " minor units in " + code
                            + ", whose minor-unit exponent it has never stated; only EUR and USD are named,"
                            + " and scaling anything else by 100 would misstate the price.",
                    provider, "unsupported_currency", false, null);
        }
        return Money.ofMinorUnits(minorUnits, MINOR_UNIT_EXPONENT, code);
    }

    /**
     * The same, answering null instead of throwing where the currency's exponent is unknown.
     *
     * <p>For the WEBHOOK path only. A webhook that has already authenticated states a fact — a
     * subscription was created, renewed, cancelled — and every SPI subscription event treats its amount
     * as optional. Refusing the whole event over a price this adapter cannot scale would drop the fact
     * and leave AgentaOS redelivering it for days; the amount is omitted, the raw payload travels on the
     * event, and nothing has been guessed.
     */
    public static Money fromMinorUnitsOrNull(final Long minorUnits, final String currency) {
        final String code = normalise(currency);
        if (minorUnits == null || code == null || !KNOWN_EXPONENT_CURRENCIES.contains(code)) {
            return null;
        }
        return Money.ofMinorUnits(minorUnits, MINOR_UNIT_EXPONENT, code);
    }

    private static String normalise(final String currency) {
        if (currency == null) {
            return null;
        }
        final String code = currency.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 3) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            final char c = code.charAt(i);
            if (c < 'A' || c > 'Z') {
                return null;
            }
        }
        return code;
    }
}
