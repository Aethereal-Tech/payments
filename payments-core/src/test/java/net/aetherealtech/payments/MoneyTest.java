package net.aetherealtech.payments;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void refusesANullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(null, "EUR"))
                .withMessageContaining("amount");
    }

    @Test
    void refusesANullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, null))
                .withMessageContaining("currency");
    }

    @Test
    void refusesACurrencyShorterThanThreeLetters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "EU"))
                .withMessageContaining("currency");
    }

    @Test
    void refusesACurrencyLongerThanThreeLetters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "EURO"))
                .withMessageContaining("currency");
    }

    @Test
    void refusesALowerCaseCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "eur"))
                .withMessageContaining("currency");
    }

    @Test
    void refusesACurrencyMadeOfDigits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "123"))
                .withMessageContaining("currency");
    }

    @Test
    void acceptsAThreeLetterUpperCaseCurrency() {
        final Money money = new Money(BigDecimal.TEN, "EUR");
        assertThat(money.currency()).isEqualTo("EUR");
    }

    @Test
    void refusesANegativeExponent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.ofMinorUnits(1999, -1, "EUR"))
                .withMessageContaining("exponent");
    }

    @Test
    void ofMinorUnitsConvertsIntoTheDecimalTheExponentImplies() {
        final Money money = Money.ofMinorUnits(1999, 2, "EUR");
        assertThat(money.amountAsString()).isEqualTo("19.99");
    }

    @Test
    void amountAsStringNeverPrintsAnExponent() {
        final Money money = new Money(new BigDecimal("9.99E+2"), "EUR");
        assertThat(money.amountAsString()).isEqualTo("999");
    }

    @Test
    void ofParsesAPlainDecimalString() {
        final Money money = Money.of("19.99", "EUR");
        assertThat(money.amountAsString()).isEqualTo("19.99");
        assertThat(money.currency()).isEqualTo("EUR");
    }

    @Test
    void aZeroOrNegativeAmountIsLegal() {
        assertThat(new Money(BigDecimal.ZERO, "EUR").amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new Money(BigDecimal.valueOf(-5), "EUR").amount()).isEqualByComparingTo(BigDecimal.valueOf(-5));
    }
}
