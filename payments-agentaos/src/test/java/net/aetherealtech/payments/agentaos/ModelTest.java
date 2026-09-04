package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.PeriodUnit;
import net.aetherealtech.payments.Recurrence;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;
import net.aetherealtech.payments.agentaos.model.BillingInterval;
import net.aetherealtech.payments.agentaos.model.CheckoutField;
import net.aetherealtech.payments.agentaos.model.CheckoutFieldType;
import net.aetherealtech.payments.agentaos.model.CheckoutStatus;
import net.aetherealtech.payments.agentaos.model.LinkType;
import net.aetherealtech.payments.agentaos.model.PaymentLinkStatus;
import net.aetherealtech.payments.agentaos.model.PendingPlanChange;
import net.aetherealtech.payments.agentaos.model.SellerMode;
import net.aetherealtech.payments.agentaos.model.Subscription;
import net.aetherealtech.payments.exception.PaymentProviderException;

/** The value types: the wire's vocabulary in, this SPI's out, and what neither of them can express. */
class ModelTest {

    @ParameterizedTest
    @CsvSource({
            "incomplete,INCOMPLETE",
            "incomplete_expired,EXPIRED",
            "trialing,TRIALING",
            "active,ACTIVE",
            "past_due,PAST_DUE",
            "canceled,CANCELLED",
            "unpaid,EXPIRED",
            "paused,PAUSED",
            "something_new,UNKNOWN"})
    void mapsEverySubscriptionStatusAgentaOsMirrorsFromStripe(final String raw, final String expected) {
        assertThat(SubscriptionStatusMapping.toSpi(raw)).isEqualTo(SubscriptionStatus.valueOf(expected));
    }

    @Test
    void anAbsentStatusIsUnknownRatherThanAGuess() {
        assertThat(SubscriptionStatusMapping.toSpi(null)).isEqualTo(SubscriptionStatus.UNKNOWN);
        assertThat(SubscriptionStatusMapping.toSpi("  ACTIVE ")).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @ParameterizedTest
    @CsvSource({
            "past_due,false",
            "active,false",
            "trialing,false",
            "incomplete,false",
            "paused,false",
            "unpaid,true",
            "canceled,true",
            "incomplete_expired,true",
            "something_new,false"})
    void knowsWhichStatusesMeanTheRetriesHaveStopped(final String raw, final boolean over) {
        assertThat(SubscriptionStatusMapping.dunningIsOver(raw)).isEqualTo(over);
    }

    @Test
    void readsEveryWireEnumAndFallsBackWhereTheServerAddsOne() {
        assertThat(CheckoutStatus.fromWire("COMPLETED")).isEqualTo(CheckoutStatus.COMPLETED);
        assertThat(CheckoutStatus.fromWire("something")).isEqualTo(CheckoutStatus.UNKNOWN);
        assertThat(CheckoutStatus.fromWire(null)).isEqualTo(CheckoutStatus.UNKNOWN);
        assertThat(CheckoutStatus.OPEN.wireValue()).isEqualTo("open");
        assertThat(CheckoutStatus.UNKNOWN.wireValue()).isNull();

        assertThat(PaymentLinkStatus.fromWire("active")).isEqualTo(PaymentLinkStatus.ACTIVE);
        assertThat(PaymentLinkStatus.fromWire("retired")).isEqualTo(PaymentLinkStatus.UNKNOWN);
        assertThat(PaymentLinkStatus.fromWire(null)).isEqualTo(PaymentLinkStatus.UNKNOWN);
        assertThat(PaymentLinkStatus.CANCELLED.wireValue()).isEqualTo("cancelled");
        assertThat(PaymentLinkStatus.UNKNOWN.wireValue()).isNull();

        assertThat(SellerMode.fromWire("crypto")).isEqualTo(SellerMode.CRYPTO);
        assertThat(SellerMode.fromWire("escrow")).isEqualTo(SellerMode.UNKNOWN);
        assertThat(SellerMode.fromWire(null)).isEqualTo(SellerMode.UNKNOWN);
        assertThat(SellerMode.MOR.wireValue()).isEqualTo("mor");
        assertThat(SellerMode.UNKNOWN.wireValue()).isNull();

        assertThat(LinkType.fromWire("one_time")).isEqualTo(LinkType.ONE_TIME);
        assertThat(LinkType.fromWire("subscription").wireValue()).isEqualTo("subscription");
        assertThat(LinkType.fromWire("lease")).isNull();
        assertThat(LinkType.fromWire(null)).isNull();

        assertThat(BillingInterval.fromWire("YEAR")).isEqualTo(BillingInterval.YEAR);
        assertThat(BillingInterval.fromWire("week")).isNull();
        assertThat(BillingInterval.fromWire(null)).isNull();
        assertThat(BillingInterval.MONTH.wireValue()).isEqualTo("month");

        assertThat(CheckoutFieldType.fromWire("tel")).isEqualTo(CheckoutFieldType.TEL);
        assertThat(CheckoutFieldType.fromWire("colour")).isNull();
        assertThat(CheckoutFieldType.fromWire(null)).isNull();
        assertThat(CheckoutFieldType.EMAIL.wireValue()).isEqualTo("email");

        assertThat(AgentaOsEventType.fromWire("send.failed")).isEqualTo(AgentaOsEventType.SEND_FAILED);
        assertThat(AgentaOsEventType.fromWire("invoice.paid")).isEqualTo(AgentaOsEventType.UNKNOWN);
        assertThat(AgentaOsEventType.fromWire(null)).isEqualTo(AgentaOsEventType.UNKNOWN);
        assertThat(AgentaOsEventType.SUBSCRIPTION_CANCELED.wireValue()).isEqualTo("subscription.canceled");
        assertThat(AgentaOsEventType.UNKNOWN.wireValue()).isNull();
    }

    @Test
    void translatesOnlyTheCadencesAgentaOsCanBill() {
        assertThat(BillingInterval.from(Recurrence.monthly())).isEqualTo(BillingInterval.MONTH);
        assertThat(BillingInterval.from(Recurrence.yearly())).isEqualTo(BillingInterval.YEAR);
        assertThat(BillingInterval.from(Recurrence.every(2, PeriodUnit.MONTH))).isNull();
        assertThat(BillingInterval.from(Recurrence.every(1, PeriodUnit.WEEK))).isNull();
        assertThat(BillingInterval.from(Recurrence.every(1, PeriodUnit.DAY))).isNull();
        assertThat(BillingInterval.from(null)).isNull();
    }

    @Test
    void undoesTheMajorUnitConvention() {
        assertThat(Amounts.fromMajorUnits(new BigDecimal("29.99"), "eur"))
                .isEqualTo(new Money(new BigDecimal("29.99"), "EUR"));
        assertThat(Amounts.fromMajorUnits("29.99", "EUR"))
                .isEqualTo(new Money(new BigDecimal("29.99"), "EUR"));
        assertThat(Amounts.fromMajorUnits((BigDecimal) null, "EUR")).isNull();
        assertThat(Amounts.fromMajorUnits(new BigDecimal("1"), null)).isNull();
        assertThat(Amounts.fromMajorUnits(new BigDecimal("1"), "EURO")).isNull();
        assertThat(Amounts.fromMajorUnits(new BigDecimal("1"), "E1R")).isNull();
        assertThat(Amounts.fromMajorUnits((String) null, "EUR")).isNull();
        assertThat(Amounts.fromMajorUnits(" ", "EUR")).isNull();
        assertThat(Amounts.fromMajorUnits("not a number", "EUR")).isNull();
    }

    @Test
    void undoesTheMinorUnitConventionForTheCurrenciesAgentaOsNames() {
        assertThat(Amounts.fromMinorUnits(1999L, "EUR", "agentaos"))
                .isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(Amounts.fromMinorUnits(1999L, "usd", "agentaos"))
                .isEqualTo(new Money(new BigDecimal("19.99"), "USD"));
        assertThat(Amounts.fromMinorUnits(null, "EUR", "agentaos")).isNull();
        assertThat(Amounts.fromMinorUnits(1999L, null, "agentaos")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"JPY", "BHD", "MKD"})
    void refusesToScaleACurrencyWhoseExponentAgentaOsNeverStated(final String currency) {
        // Two decimals is right for EUR and USD and wrong for a zero- or three-decimal currency, and
        // AgentaOS's subscriptions are Stripe-backed, where exactly those are in use.
        final PaymentProviderException e = catchThrowableOfType(PaymentProviderException.class,
                () -> Amounts.fromMinorUnits(1999L, currency, "agentaos"));

        assertThat(e.code()).isEqualTo("unsupported_currency");
        assertThat(e).hasMessageContaining(currency);
        assertThat(Amounts.fromMinorUnitsOrNull(1999L, currency)).isNull();
    }

    @Test
    void theWebhookPathOmitsSuchAPriceRatherThanRefusingTheWholeEvent() {
        assertThat(Amounts.fromMinorUnitsOrNull(1999L, "EUR"))
                .isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(Amounts.fromMinorUnitsOrNull(null, "EUR")).isNull();
        assertThat(Amounts.fromMinorUnitsOrNull(1999L, null)).isNull();
    }

    @Test
    void aCheckoutFieldRoundTripsItsWireShape() {
        final CheckoutField field = new CheckoutField("size", "Team size", CheckoutFieldType.SELECT, true,
                "pick one", List.of("1-10", "11-50"));

        assertThat(Json.write(field.toWire())).isEqualTo(
                "{\"key\":\"size\",\"label\":\"Team size\",\"type\":\"select\",\"required\":true,"
                        + "\"placeholder\":\"pick one\",\"options\":[\"1-10\",\"11-50\"]}");
        assertThat(CheckoutField.from(Json.parseObject(Json.write(field.toWire())))).isEqualTo(field);
    }

    @Test
    void aCheckoutFieldNeedsAKey() {
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new CheckoutField(" ", "l", CheckoutFieldType.TEXT, true, null, null)))
                .hasMessageContaining("key must not be blank");
    }

    @Test
    void aCheckoutFieldWithNoTypeWritesNone() {
        final CheckoutField field = new CheckoutField("k", "l", null, false, null, null);

        assertThat(Json.write(field.toWire()))
                .isEqualTo("{\"key\":\"k\",\"label\":\"l\",\"required\":false}");
    }

    @Test
    void aPendingPlanChangeWithNoLinkNamesNoPlan() {
        assertThat(PendingPlanChange.from(null, "EUR")).isNull();
        assertThat(new PendingPlanChange(null, "x", null, null).asPlan()).isNull();
    }

    @Test
    void aSubscriptionWithNoLinkNamesNoPlan() {
        final Map<String, Object> body = Json.parseObject("{\"id\":\"sub_1\",\"status\":\"active\"}");

        assertThat(Subscription.from(body).plan()).isNull();
    }
}
