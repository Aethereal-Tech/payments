package net.aetherealtech.payments;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentIntentTest {

    @Test
    void refusesANullMerchantReference() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentIntent(
                        null, new Money(BigDecimal.TEN, "EUR"), null, null,
                        null, null, null, null, null, null, null))
                .withMessageContaining("merchantReference");
    }

    @Test
    void refusesABlankMerchantReference() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentIntent(
                        "   ", new Money(BigDecimal.TEN, "EUR"), null, null,
                        null, null, null, null, null, null, null))
                .withMessageContaining("merchantReference");
    }

    @Test
    void refusesNeitherAnAmountNorAPlanRef() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentIntent(
                        "order-1", null, null, null,
                        null, null, null, null, null, null, null))
                .withMessageContaining("amount")
                .withMessageContaining("planRef");
    }

    @Test
    void aBlankPlanRefDoesNotCountAsPresent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentIntent(
                        "order-1", null, "   ", null,
                        null, null, null, null, null, null, null));
    }

    @Test
    void metadataIsCopiedRatherThanAliased() {
        final Map<String, String> source = new HashMap<>();
        source.put("orderSource", "web");
        final PaymentIntent intent = new PaymentIntent(
                "order-1", new Money(BigDecimal.TEN, "EUR"), null, null,
                null, null, null, null, null, null, source);

        source.put("orderSource", "mobile");
        source.put("extra", "value");

        assertThat(intent.metadata()).containsExactly(Map.entry("orderSource", "web"));
    }

    @Test
    void metadataIsAnEmptyMapNeverNullWhenNoneWasGiven() {
        final PaymentIntent intent = new PaymentIntent(
                "order-1", new Money(BigDecimal.TEN, "EUR"), null, null,
                null, null, null, null, null, null, null);

        assertThat(intent.metadata()).isNotNull().isEmpty();
    }

    @Test
    void isRecurringIsFalseForAnAmountOnlyIntent() {
        final PaymentIntent intent = new PaymentIntent(
                "order-1", new Money(BigDecimal.TEN, "EUR"), null, null,
                null, null, null, null, null, null, null);

        assertThat(intent.isRecurring()).isFalse();
    }

    @Test
    void isRecurringIsTrueForAPlanRefOnlyIntent() {
        final PaymentIntent intent = new PaymentIntent(
                "order-1", null, "plan-1", null,
                null, null, null, null, null, null, null);

        assertThat(intent.isRecurring()).isTrue();
    }

    @Test
    void isRecurringIsTrueForARecurrenceOnlyIntent() {
        final PaymentIntent intent = new PaymentIntent(
                "order-1", new Money(BigDecimal.TEN, "EUR"), null, Recurrence.monthly(),
                null, null, null, null, null, null, null);

        assertThat(intent.isRecurring()).isTrue();
    }

    @Test
    void isRecurringIsTrueWhenBothPlanRefAndRecurrenceArePresent() {
        final PaymentIntent intent = new PaymentIntent(
                "order-1", null, "plan-1", Recurrence.monthly(),
                null, null, null, null, null, null, null);

        assertThat(intent.isRecurring()).isTrue();
    }

    @Test
    void builderSetsEveryField() {
        final Money amount = new Money(BigDecimal.TEN, "EUR");
        final Recurrence recurrence = Recurrence.monthly();
        final Customer customer = Customer.withEmail("buyer@example.com");
        final Map<String, String> metadata = Map.of("orderSource", "web");

        final PaymentIntent intent = PaymentIntent.builder("order-1")
                .amount(amount)
                .planRef("plan-1")
                .recurrence(recurrence)
                .description("Gold membership")
                .successUrl("https://example.com/success")
                .cancelUrl("https://example.com/cancel")
                .errorUrl("https://example.com/error")
                .callbackUrl("https://example.com/callback")
                .customer(customer)
                .metadata(metadata)
                .build();

        assertThat(intent.merchantReference()).isEqualTo("order-1");
        assertThat(intent.amount()).isEqualTo(amount);
        assertThat(intent.planRef()).isEqualTo("plan-1");
        assertThat(intent.recurrence()).isEqualTo(recurrence);
        assertThat(intent.description()).isEqualTo("Gold membership");
        assertThat(intent.successUrl()).isEqualTo("https://example.com/success");
        assertThat(intent.cancelUrl()).isEqualTo("https://example.com/cancel");
        assertThat(intent.errorUrl()).isEqualTo("https://example.com/error");
        assertThat(intent.callbackUrl()).isEqualTo("https://example.com/callback");
        assertThat(intent.customer()).isEqualTo(customer);
        assertThat(intent.metadata()).isEqualTo(metadata);
    }

    @Test
    void builderUrlsSetsAllFourUrlsAtOnce() {
        final PaymentIntent intent = PaymentIntent.builder("order-1")
                .amount(new Money(BigDecimal.TEN, "EUR"))
                .urls("https://example.com/success", "https://example.com/cancel",
                        "https://example.com/error", "https://example.com/callback")
                .build();

        assertThat(intent.successUrl()).isEqualTo("https://example.com/success");
        assertThat(intent.cancelUrl()).isEqualTo("https://example.com/cancel");
        assertThat(intent.errorUrl()).isEqualTo("https://example.com/error");
        assertThat(intent.callbackUrl()).isEqualTo("https://example.com/callback");
    }
}
