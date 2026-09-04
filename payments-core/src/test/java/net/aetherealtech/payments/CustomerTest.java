package net.aetherealtech.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void withEmailSetsOnlyTheEmail() {
        final Customer customer = Customer.withEmail("buyer@example.com");
        assertThat(customer.email()).isEqualTo("buyer@example.com");
        assertThat(customer.reference()).isNull();
        assertThat(customer.name()).isNull();
        assertThat(customer.countryCode()).isNull();
        assertThat(customer.vatNumber()).isNull();
    }

    @Test
    void builderSetsEveryField() {
        final Customer customer = Customer.builder()
                .reference("cust-1")
                .email("buyer@example.com")
                .name("Buyer Name")
                .countryCode("MK")
                .vatNumber("MK1234567")
                .build();

        assertThat(customer.reference()).isEqualTo("cust-1");
        assertThat(customer.email()).isEqualTo("buyer@example.com");
        assertThat(customer.name()).isEqualTo("Buyer Name");
        assertThat(customer.countryCode()).isEqualTo("MK");
        assertThat(customer.vatNumber()).isEqualTo("MK1234567");
    }
}
