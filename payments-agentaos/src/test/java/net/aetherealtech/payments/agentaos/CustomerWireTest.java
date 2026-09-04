package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.agentaos.model.Customer;

/** Customers: read-only, list-only. */
class CustomerWireTest extends GatewayTestBase {

    @Test
    void listsTheBuyersWhoHavePaid() {
        stubGet("/api/v1/gateway/customers?limit=20&offset=0", Fixtures.load("customers.json"));

        final Customer customer = client().listCustomers(20, 0).items().getFirst();

        assertThat(customer.id()).isEqualTo("cus_31");
        assertThat(customer.email()).isEqualTo("buyer@example.com");
        assertThat(customer.name()).isEqualTo("A Buyer");
        assertThat(customer.country()).isEqualTo("MK");
        assertThat(customer.vatNumber()).isEqualTo("MK4030000000000");
        assertThat(customer.stripeCustomerId()).isEqualTo("cus_stripe_31");
        assertThat(customer.createdAt()).isEqualTo(Instant.parse("2026-07-15T08:30:00Z"));
        assertThat(onlyRequest().getMethod().getName()).isEqualTo("GET");
    }

    @Test
    void convertsToTheSpisOwnCustomer() {
        stubGet("/api/v1/gateway/customers?limit=20&offset=0", Fixtures.load("customers.json"));

        final net.aetherealtech.payments.Customer spi =
                client().listCustomers(20, 0).items().getFirst().toSpi();

        assertThat(spi.reference()).isEqualTo("cus_31");
        assertThat(spi.email()).isEqualTo("buyer@example.com");
        assertThat(spi.countryCode()).isEqualTo("MK");
        assertThat(spi.vatNumber()).isEqualTo("MK4030000000000");
    }
}
