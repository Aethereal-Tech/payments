package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.SubscriptionStatus;
import net.aetherealtech.payments.agentaos.model.BillingInterval;
import net.aetherealtech.payments.agentaos.model.CancelSubscriptionResult;
import net.aetherealtech.payments.agentaos.model.Page;
import net.aetherealtech.payments.agentaos.model.Subscription;

/** Subscriptions: the list envelope, the minor-unit prices, and the cancel body. */
class SubscriptionWireTest extends GatewayTestBase {

    private static final String SUBSCRIPTIONS = "/api/v1/gateway/subscriptions";

    @Test
    void listSendsAnExplicitLimitAndOffsetAndReadsTheEnvelope() {
        stubGet(SUBSCRIPTIONS + "?limit=100&offset=0", Fixtures.load("subscriptions-page-1.json"));

        final Page<Subscription> page = client().listSubscriptions(100, 0);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).hasSize(1);
        assertThat(onlyRequest().getUrl()).isEqualTo(SUBSCRIPTIONS + "?limit=100&offset=0");
    }

    @Test
    void readsASubscriptionsFieldsIncludingItsMinorUnitPrice() {
        stubGet(SUBSCRIPTIONS + "?limit=50&offset=1", Fixtures.load("subscriptions-page-2.json"));

        final Subscription subscription = client().listSubscriptions(50, 1).items().getFirst();

        assertThat(subscription.id()).isEqualTo("sub_bbb");
        assertThat(subscription.customerEmail()).isEqualTo("second@example.com");
        assertThat(subscription.customerName()).isEqualTo("Second Buyer");
        assertThat(subscription.planName()).isEqualTo("Pro");
        assertThat(subscription.linkId()).isEqualTo("pl_9d3");
        assertThat(subscription.billingInterval()).isEqualTo(BillingInterval.YEAR);
        assertThat(subscription.rawStatus()).isEqualTo("past_due");
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        // 1999 minor units, not 1999 euro.
        assertThat(subscription.amount()).isEqualTo(new Money(new BigDecimal("19.99"), "EUR"));
        assertThat(subscription.currentPeriodEnd()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(subscription.stripeSubscriptionId()).isEqualTo("sub_stripe_bbb");
        assertThat(subscription.cancelAtPeriodEnd()).isTrue();
        assertThat(subscription.canceledAt()).isEqualTo(Instant.parse("2026-08-20T10:00:00Z"));
        assertThat(subscription.effectiveCancelDate()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(subscription.plan().ref()).isEqualTo("pl_9d3");
        assertThat(subscription.pendingPlanChange().linkId()).isEqualTo("pl_starter");
        assertThat(subscription.pendingPlanChange().planName()).isEqualTo("Starter");
        assertThat(subscription.pendingPlanChange().amount())
                .isEqualTo(new Money(new BigDecimal("9.99"), "EUR"));
        assertThat(subscription.pendingPlanChange().effectiveAt())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(subscription.pendingPlanChange().asPlan().displayName()).isEqualTo("Starter");
    }

    @Test
    void cancelPostsTheFlagExplicitlyEitherWay() {
        stubJson(SUBSCRIPTIONS + "/sub_bbb/cancel", 200, Fixtures.load("subscription-cancel.json"));

        final CancelSubscriptionResult result = client().cancelSubscription("sub_bbb", true);

        assertThat(result.rawStatus()).isEqualTo("active");
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.cancelAtPeriodEnd()).isTrue();
        assertThat(result.currentPeriodEnd()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(result.effectiveCancelDate()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));

        final LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("POST");
        assertThat(request.getBodyAsString()).isEqualTo("{\"atPeriodEnd\":true}");
        assertThat(request.getHeader("content-type")).isEqualTo(JSON);
    }

    @Test
    void cancelSendsFalseRatherThanOmittingIt() {
        stubJson(SUBSCRIPTIONS + "/sub_bbb/cancel", 200, Fixtures.load("subscription-cancel.json"));

        client().cancelSubscription("sub_bbb", false);

        // The SDK defaults an absent flag to true. A default that decides whether somebody keeps the
        // access they paid for is not one to inherit silently.
        assertThat(onlyRequest().getBodyAsString()).isEqualTo("{\"atPeriodEnd\":false}");
    }

    @Test
    void refusesAPageSizeOutsideTheDocumentedRange() {
        final AgentaOsClient client = client();

        assertThatThrownBy(() -> client.listSubscriptions(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be between 1 and 100");
        assertThatThrownBy(() -> client.listSubscriptions(101, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.listSubscriptions(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset must not be negative");
    }

    @Test
    void readsAnEmptyPage() {
        stubGet(SUBSCRIPTIONS + "?limit=100&offset=0", Fixtures.load("subscriptions-empty.json"));

        final Page<Subscription> page = client().listSubscriptions(100, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.hasMore()).isFalse();
        assertThat(Page.empty().items()).isEmpty();
    }
}
