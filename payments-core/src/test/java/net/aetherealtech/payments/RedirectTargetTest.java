package net.aetherealtech.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RedirectTargetTest {

    @Test
    void refusesANullRedirectUrl() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RedirectTarget(null, false, null, "checkout-1"))
                .withMessageContaining("redirectUrl");
    }

    @Test
    void refusesABlankRedirectUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RedirectTarget("   ", false, null, "checkout-1"))
                .withMessageContaining("redirectUrl");
    }

    @Test
    void ofBuildsAPlainBrowserRedirectWithNoMachinePaymentRail() {
        final RedirectTarget target = RedirectTarget.of("https://pay.example/checkout", "checkout-1");
        assertThat(target.redirectUrl()).isEqualTo("https://pay.example/checkout");
        assertThat(target.iframe()).isFalse();
        assertThat(target.checkoutRef()).isEqualTo("checkout-1");
        assertThat(target.machinePayment()).isEmpty();
    }

    @Test
    void machinePaymentIsPresentWhenTheProviderOffersOne() {
        final RedirectTarget target = new RedirectTarget(
                "https://pay.example/checkout", true, "https://pay.example/x402", "checkout-1");
        assertThat(target.machinePayment()).contains("https://pay.example/x402");
        assertThat(target.iframe()).isTrue();
    }
}
