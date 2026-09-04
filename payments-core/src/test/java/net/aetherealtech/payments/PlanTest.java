package net.aetherealtech.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlanTest {

    @Test
    void refusesANullRef() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Plan(null, "Name"))
                .withMessageContaining("ref");
    }

    @Test
    void refusesABlankRef() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Plan("   ", "Name"))
                .withMessageContaining("ref");
    }

    @Test
    void displayNameFallsBackToRefWhenNoNameWasGiven() {
        final Plan plan = Plan.of("plan-1");
        assertThat(plan.name()).isNull();
        assertThat(plan.displayName()).isEqualTo("plan-1");
    }

    @Test
    void displayNameUsesTheNameWhenGiven() {
        final Plan plan = Plan.of("plan-1", "Gold Plan");
        assertThat(plan.displayName()).isEqualTo("Gold Plan");
    }
}
