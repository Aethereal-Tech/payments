package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.agentaos.model.BillingInterval;
import net.aetherealtech.payments.agentaos.model.CheckoutField;
import net.aetherealtech.payments.agentaos.model.CheckoutFieldType;
import net.aetherealtech.payments.agentaos.model.CreatePaymentLinkRequest;
import net.aetherealtech.payments.agentaos.model.LinkType;
import net.aetherealtech.payments.agentaos.model.PaymentLink;
import net.aetherealtech.payments.agentaos.model.PaymentLinkStatus;
import net.aetherealtech.payments.agentaos.model.SellerMode;

/** Payment links, including the one place a cancel is a DELETE rather than a POST. */
class PaymentLinkWireTest extends GatewayTestBase {

    private static final String LINKS = "/api/v1/gateway/payment-links";

    @Test
    void createPostsACamelCaseBody() {
        stubJson(LINKS, 200, Fixtures.load("payment-link.json"));

        client().createPaymentLink(CreatePaymentLinkRequest
                .subscription(Money.of("19.99", "EUR"), BillingInterval.MONTH)
                .name("Pro")
                .description("Pro plan")
                .imageUrl("https://cdn.example/pro.png")
                .webhookUrl("https://shop.example/hooks/agentaos")
                .urls("https://shop.example/thanks", "https://shop.example/cart")
                .metadata(Map.of("tier", "pro"))
                .expiresAt(Instant.parse("2026-12-31T23:59:00Z"))
                .taxRateId("tr_18")
                .checkoutFields(List.of(CheckoutField.text("vat", "VAT number")))
                .trialPeriodDays(14)
                .build());

        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("POST");
        assertThat(request.getUrl()).isEqualTo(LINKS);
        assertThat(request.getBodyAsString()).isEqualTo(
                "{\"amount\":19.99,\"currency\":\"EUR\",\"description\":\"Pro plan\",\"name\":\"Pro\","
                        + "\"imageUrl\":\"https://cdn.example/pro.png\","
                        + "\"webhookUrl\":\"https://shop.example/hooks/agentaos\","
                        + "\"successUrl\":\"https://shop.example/thanks\","
                        + "\"cancelUrl\":\"https://shop.example/cart\",\"metadata\":{\"tier\":\"pro\"},"
                        + "\"expiresAt\":\"2026-12-31T23:59:00Z\",\"taxRateId\":\"tr_18\","
                        + "\"checkoutFields\":[{\"key\":\"vat\",\"label\":\"VAT number\",\"type\":\"text\","
                        + "\"required\":true}],\"type\":\"subscription\",\"billingInterval\":\"month\","
                        + "\"trialPeriodDays\":14}");
        assertThat(request.getHeader("idempotency-key")).isNotNull();
    }

    @Test
    void readsTheResponseAsSnakeCase() {
        stubJson(LINKS, 200, Fixtures.load("payment-link.json"));

        final PaymentLink link = client().createPaymentLink(
                CreatePaymentLinkRequest.oneTime(Money.of("19.99", "EUR")).build(), "seed-1");

        assertThat(link.id()).isEqualTo("pl_9d3");
        assertThat(link.orgId()).isEqualTo("org_7fc1");
        assertThat(link.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(link.status()).isEqualTo(PaymentLinkStatus.ACTIVE);
        assertThat(link.sellerMode()).isEqualTo(SellerMode.MOR);
        assertThat(link.type()).isEqualTo(LinkType.SUBSCRIPTION);
        assertThat(link.billingInterval()).isEqualTo(BillingInterval.MONTH);
        assertThat(link.trialPeriodDays()).isEqualTo(14);
        assertThat(link.paymentCount()).isEqualTo(42);
        assertThat(link.imageUrl()).isEqualTo("https://cdn.example/pro.png");
        assertThat(link.taxRateId()).isEqualTo("tr_18");
        assertThat(link.expiresAt()).isNull();
        assertThat(link.createdAt()).isEqualTo(Instant.parse("2026-08-01T09:00:00Z"));
        assertThat(link.updatedAt()).isEqualTo(Instant.parse("2026-08-20T09:00:00Z"));
        assertThat(link.metadata()).containsEntry("tier", "pro");
        assertThat(link.asPlan().ref()).isEqualTo("pl_9d3");
        assertThat(link.asPlan().displayName()).isEqualTo("Pro");
        assertThat(link.checkoutFields()).hasSize(2);
        assertThat(link.checkoutFields().getFirst().type()).isEqualTo(CheckoutFieldType.TEXT);
        assertThat(link.checkoutFields().getFirst().required()).isFalse();
        assertThat(link.checkoutFields().getLast().type()).isEqualTo(CheckoutFieldType.SELECT);
        assertThat(link.checkoutFields().getLast().options()).containsExactly("1-10", "11-50");
        assertThat(onlyRequest().getHeader("idempotency-key")).isEqualTo("seed-1");
    }

    @Test
    void retrieveGetsTheLink() {
        stubGet(LINKS + "/pl_9d3", Fixtures.load("payment-link.json"));

        assertThat(client().retrievePaymentLink("pl_9d3").id()).isEqualTo("pl_9d3");
        assertThat(onlyRequest().getMethod().getName()).isEqualTo("GET");
    }

    @Test
    void cancelIsADeleteHereWhereACheckoutsCancelIsAPost() {
        stubJson(LINKS + "/pl_9d3", 200, Fixtures.load("payment-link-cancelled.json"));

        final PaymentLink cancelled = client().cancelPaymentLink("pl_9d3");

        assertThat(cancelled.status()).isEqualTo(PaymentLinkStatus.CANCELLED);
        assertThat(cancelled.checkoutFields()).isEmpty();
        assertThat(cancelled.trialPeriodDays()).isNull();
        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("DELETE");
        assertThat(request.getUrl()).isEqualTo(LINKS + "/pl_9d3");
        assertThat(request.getHeader("idempotency-key")).isNull();
        assertThat(request.getHeader("content-type")).isNull();
    }

    @Test
    void refusesASubscriptionLinkWithNoBillingInterval() {
        assertThatThrownBy(() -> new CreatePaymentLinkRequest(Money.of("1.00", "EUR"), null, null, null, null,
                null, null, null, null, null, null, LinkType.SUBSCRIPTION, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billingInterval");
    }

    @Test
    void refusesATrialOutsideTheRangeAgentaOsAccepts() {
        assertThatThrownBy(() -> CreatePaymentLinkRequest.oneTime(Money.of("1.00", "EUR"))
                .trialPeriodDays(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trialPeriodDays");
        assertThatThrownBy(() -> CreatePaymentLinkRequest.oneTime(Money.of("1.00", "EUR"))
                .trialPeriodDays(731).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesALinkWithNoPrice() {
        assertThatThrownBy(() -> CreatePaymentLinkRequest.oneTime(null).build())
                .isInstanceOf(NullPointerException.class);
    }
}
