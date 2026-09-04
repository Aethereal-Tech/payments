package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Which header a key belongs in, and what is refused before a request is ever built. */
class AgentaOsConfigTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl";

    @ParameterizedTest
    @ValueSource(strings = {"sk_live_abc", "sk_test_abc"})
    void adashboardKeyTravelsAsXApiKey(final String key) {
        final AgentaOsConfig config = AgentaOsConfig.of(key);

        assertThat(config.authMode()).isEqualTo(AuthMode.API_KEY);
        assertThat(config.authMode().headerName()).isEqualTo("x-api-key");
        assertThat(config.authHeaderValue()).isEqualTo(key);
    }

    @Test
    void aSessionTokenTravelsAsABearerAuthorization() {
        final AgentaOsConfig config = AgentaOsConfig.of(JWT);

        assertThat(config.authMode()).isEqualTo(AuthMode.JWT);
        assertThat(config.authMode().headerName()).isEqualTo("authorization");
        assertThat(config.authHeaderValue()).isEqualTo("Bearer " + JWT);
    }

    @Test
    void aTokenWithAnEmptyThirdSegmentIsStillAJwt() {
        // JavaScript's split('.') keeps the trailing empty part, and the SDK counts three. A token this
        // refused but the SDK accepted would be an integration that works everywhere except here.
        assertThat(AgentaOsConfig.of("header.payload.").authMode()).isEqualTo(AuthMode.JWT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pk_live_abc", "abc", "a.b", "a.b.c.d", "sk_abc", "SK_LIVE_abc"})
    void refusesAKeyThatIsNeitherAtConstruction(final String key) {
        assertThatThrownBy(() -> AgentaOsConfig.of(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sk_live_")
                .hasMessageContaining("sk_test_")
                .hasMessageContaining("JWT");
    }

    @Test
    void refusesABlankKey() {
        assertThatThrownBy(() -> AgentaOsConfig.of("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey must not be blank");
        assertThatThrownBy(() -> AgentaOsConfig.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsToTheOnlyHostAgentaOsPublishes() {
        assertThat(AgentaOsConfig.of("sk_test_abc").baseUrl())
                .isEqualTo(URI.create("https://api.agentaos.ai"));
        assertThat(AgentaOsConfig.DEFAULT_WEBHOOK_TOLERANCE).isEqualTo(Duration.ofSeconds(300));
        assertThat(AgentaOsConfig.DEFAULT_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void stripsATrailingSlashSoPathsDoNotDouble() {
        assertThat(AgentaOsConfig.of(URI.create("http://localhost:8080/"), "sk_test_abc").baseUrl())
                .hasToString("http://localhost:8080");
    }

    @Test
    void verifiesWebhooksOnlyWithASecret() {
        assertThat(AgentaOsConfig.of("sk_test_abc").verifiesWebhooks()).isFalse();
        assertThat(AgentaOsConfig.of("sk_test_abc").withWebhookSecret("  ").verifiesWebhooks()).isFalse();
        assertThat(AgentaOsConfig.of("sk_test_abc").withWebhookSecret("whsec_x").verifiesWebhooks()).isTrue();
    }

    @Test
    void carriesEveryOverrideThrough() {
        final AgentaOsConfig config = AgentaOsConfig.of("sk_test_abc")
                .withWebhookSecret("whsec_x")
                .withTimeout(Duration.ofSeconds(5))
                .withWebhookTolerance(Duration.ofSeconds(60))
                .withPageSize(25);

        assertThat(config.webhookSecret()).isEqualTo("whsec_x");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.webhookTolerance()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.pageSize()).isEqualTo(25);
    }

    @Test
    void refusesNonsensicalTimeoutsAndPageSizes() {
        final AgentaOsConfig valid = AgentaOsConfig.of("sk_test_abc");

        assertThatThrownBy(() -> valid.withTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout must be positive");
        assertThatThrownBy(() -> valid.withTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid.withWebhookTolerance(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhookTolerance");
        assertThatThrownBy(() -> valid.withPageSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
        assertThatThrownBy(() -> valid.withPageSize(101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentaOsConfig(null, "sk_test_abc", null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10))
                .isInstanceOf(NullPointerException.class);
    }
}
