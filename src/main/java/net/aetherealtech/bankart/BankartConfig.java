package net.aetherealtech.bankart;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Everything the client needs to reach one connector.
 *
 * <p>{@code sharedSecret} may be null: request signing is a per-connector setting
 * ("API: Enable Request Signing") that the gateway administrators control, and the docs describe it
 * as optional-but-recommended. Notification verification, however, needs it regardless — the
 * gateway signs its notifications with the same secret.
 *
 * @param baseUrl      the API root, including the version path, e.g. {@code https://gateway.bankart.si/api/v3}
 * @param apiKey       the connector's API key, which appears in every request path
 * @param username     API user for HTTP Basic auth
 * @param password     API password for HTTP Basic auth
 * @param sharedSecret connector shared secret for request signing; null disables the X-Signature header
 * @param timeout      per-request timeout
 */
public record BankartConfig(
        URI baseUrl,
        String apiKey,
        String username,
        String password,
        String sharedSecret,
        Duration timeout) {

    public static final URI PRODUCTION_BASE_URL = URI.create("https://gateway.bankart.si/api/v3");

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public BankartConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        requireText(apiKey, "apiKey");
        requireText(username, "username");
        requireText(password, "password");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        // A trailing slash would produce "//transaction/..." in both the URL and the signed URI, and
        // the second of those fails in a way that points nowhere near the cause.
        String path = baseUrl.getPath();
        if (path != null && path.endsWith("/")) {
            baseUrl = URI.create(baseUrl.toString().substring(0, baseUrl.toString().length() - 1));
        }
    }

    /** The production gateway, with signing enabled. */
    public static BankartConfig production(String apiKey, String username, String password, String sharedSecret) {
        return new BankartConfig(PRODUCTION_BASE_URL, apiKey, username, password, sharedSecret, DEFAULT_TIMEOUT);
    }

    public static BankartConfig of(URI baseUrl, String apiKey, String username, String password) {
        return new BankartConfig(baseUrl, apiKey, username, password, null, DEFAULT_TIMEOUT);
    }

    public BankartConfig withSharedSecret(String sharedSecret) {
        return new BankartConfig(baseUrl, apiKey, username, password, sharedSecret, timeout);
    }

    public BankartConfig withTimeout(Duration timeout) {
        return new BankartConfig(baseUrl, apiKey, username, password, sharedSecret, timeout);
    }

    public boolean signsRequests() {
        return sharedSecret != null && !sharedSecret.isEmpty();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
