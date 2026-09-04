package net.aetherealtech.payments.bankart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankartConfigTest {

    private static final URI BASE = URI.create("https://gateway.bankart.si/api/v3");

    @Test
    void productionPointsAtTheDocumentedEndpoint() {
        BankartConfig config = BankartConfig.production("key", "user", "pass", "secret");

        assertThat(config.baseUrl()).hasToString("https://gateway.bankart.si/api/v3");
        assertThat(BankartConfig.PRODUCTION_BASE_URL).isEqualTo(BASE);
        assertThat(config.signsRequests()).isTrue();
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("a trailing slash is trimmed, since it would corrupt the signed URI as well as the URL")
    void trimsATrailingSlash() {
        BankartConfig config = BankartConfig.of(
                URI.create("https://gateway.bankart.si/api/v3/"), "key", "user", "pass");

        assertThat(config.baseUrl()).hasToString("https://gateway.bankart.si/api/v3");
    }

    @Test
    void signingIsOffUntilASecretIsGiven() {
        BankartConfig unsigned = BankartConfig.of(BASE, "key", "user", "pass");

        assertThat(unsigned.signsRequests()).isFalse();
        assertThat(unsigned.withSharedSecret("").signsRequests()).isFalse();
        assertThat(unsigned.withSharedSecret("secret").signsRequests()).isTrue();
        assertThat(unsigned.withSharedSecret("secret").sharedSecret()).isEqualTo("secret");
    }

    @Test
    void withersKeepEverythingElse() {
        BankartConfig config = BankartConfig.production("key", "user", "pass", "secret")
                .withTimeout(Duration.ofSeconds(5));

        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.apiKey()).isEqualTo("key");
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.password()).isEqualTo("pass");
        assertThat(config.sharedSecret()).isEqualTo("secret");
        assertThat(config.withSharedSecret("other").timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void rejectsIncompleteCredentials() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BankartConfig.of(BASE, "", "user", "pass"))
                .withMessageContaining("apiKey");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BankartConfig.of(BASE, "key", " ", "pass"))
                .withMessageContaining("username");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BankartConfig.of(BASE, "key", "user", null))
                .withMessageContaining("password");
        assertThatNullPointerException()
                .isThrownBy(() -> BankartConfig.of(null, "key", "user", "pass"));
    }

    @Test
    void rejectsANonPositiveTimeout() {
        BankartConfig config = BankartConfig.of(BASE, "key", "user", "pass");

        assertThatIllegalArgumentException().isThrownBy(() -> config.withTimeout(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> config.withTimeout(Duration.ofSeconds(-1)));
        assertThatNullPointerException().isThrownBy(() -> config.withTimeout(null));
    }

    @Test
    void theClientRefusesToBeBuiltWithoutItsCollaborators() {
        BankartConfig config = BankartConfig.of(BASE, "key", "user", "pass");

        assertThatNullPointerException().isThrownBy(() -> new BankartClient(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new BankartClient(config, null, null, null));
        assertThat(new BankartClient(config).config()).isSameAs(config);
    }
}
