package net.aetherealtech.payments.agentaos;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import net.aetherealtech.payments.Provisional;

/**
 * Everything the client and the webhook verifier need to reach one AgentaOS organization.
 *
 * <p>{@code webhookSecret} may be null: an integration that only creates checkouts and polls never
 * receives a webhook, and demanding a secret it has no use for would be a startup failure with no cause.
 * A provider asked to handle a webhook without one refuses the request rather than reading it.
 *
 * <p>There is no sandbox host. AgentaOS uses the same {@link #PRODUCTION_BASE_URL} for live and test
 * keys and distinguishes them by the key prefix alone, so {@code baseUrl} is an override for a local
 * stub or a staging deployment rather than an environment switch.
 *
 * @param baseUrl          the API root WITHOUT the version segment — that lives in each resource path
 * @param apiKey           an {@code sk_live_}/{@code sk_test_} key, or a three-part JWT session token
 * @param webhookSecret    the {@code whsec_…} secret webhooks are signed with, or null
 * @param timeout          per-request timeout
 * @param webhookTolerance how far a webhook's signed timestamp may be from now, in either direction
 * @param pageSize         how many rows to ask a list endpoint for, 1–100
 */
public record AgentaOsConfig(
        URI baseUrl,
        String apiKey,
        String webhookSecret,
        Duration timeout,
        Duration webhookTolerance,
        int pageSize) {

    /** The only host AgentaOS publishes; live and test keys both go here. */
    public static final URI PRODUCTION_BASE_URL = URI.create("https://api.agentaos.ai");

    /** The SDK's own default, and the window a signed webhook timestamp must fall inside. */
    public static final Duration DEFAULT_WEBHOOK_TOLERANCE = Duration.ofSeconds(300);

    /**
     * How many rows a list request asks for.
     *
     * <p>An explicit limit on every list, never the server's own default, because there is no agreement
     * on what that default is; 100 is the documented maximum, which is what a reconcile paging through
     * subscriptions wants.
     */
    @Provisional("The SDK's types.ts documents ListParams.limit as \"1-100, default 10\" while "
            + "packages/pay/README.md documents default 20 for the same calls and the wallet CLI defaults "
            + "to 10 — three statements, no way to tell which the server applies, so this never relies on it.")
    public static final int DEFAULT_PAGE_SIZE = 100;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final String LIVE_PREFIX = "sk_live_";
    private static final String TEST_PREFIX = "sk_test_";

    public AgentaOsConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(webhookTolerance, "webhookTolerance must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (detect(apiKey) == null) {
            throw new IllegalArgumentException(
                    "Invalid API key format. Must start with " + LIVE_PREFIX + " or " + TEST_PREFIX
                            + ", or be a three-part dotted JWT session token.");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (webhookTolerance.isNegative()) {
            throw new IllegalArgumentException("webhookTolerance must not be negative");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100, got " + pageSize);
        }
        // A trailing slash would build "//api/v1/gateway/sessions", which some routers answer with a
        // redirect this client does not follow and others with a 404 pointing nowhere near the cause.
        final String text = baseUrl.toString();
        if (text.endsWith("/")) {
            baseUrl = URI.create(text.substring(0, text.length() - 1));
        }
    }

    /** The published host, with no webhook secret. */
    public static AgentaOsConfig of(final String apiKey) {
        return new AgentaOsConfig(PRODUCTION_BASE_URL, apiKey, null, DEFAULT_TIMEOUT,
                DEFAULT_WEBHOOK_TOLERANCE, DEFAULT_PAGE_SIZE);
    }

    /** Another host — a local stub, or a staging deployment. */
    public static AgentaOsConfig of(final URI baseUrl, final String apiKey) {
        return new AgentaOsConfig(baseUrl, apiKey, null, DEFAULT_TIMEOUT,
                DEFAULT_WEBHOOK_TOLERANCE, DEFAULT_PAGE_SIZE);
    }

    public AgentaOsConfig withWebhookSecret(final String webhookSecret) {
        return new AgentaOsConfig(baseUrl, apiKey, webhookSecret, timeout, webhookTolerance, pageSize);
    }

    public AgentaOsConfig withTimeout(final Duration timeout) {
        return new AgentaOsConfig(baseUrl, apiKey, webhookSecret, timeout, webhookTolerance, pageSize);
    }

    public AgentaOsConfig withWebhookTolerance(final Duration webhookTolerance) {
        return new AgentaOsConfig(baseUrl, apiKey, webhookSecret, timeout, webhookTolerance, pageSize);
    }

    public AgentaOsConfig withPageSize(final int pageSize) {
        return new AgentaOsConfig(baseUrl, apiKey, webhookSecret, timeout, webhookTolerance, pageSize);
    }

    /** Which header {@link #apiKey()} belongs in, from the shape of the key. Never null. */
    public AuthMode authMode() {
        return detect(apiKey);
    }

    /** The value for {@link AuthMode#headerName()}, ready to set. */
    public String authHeaderValue() {
        return authMode().headerValue(apiKey);
    }

    /** Whether webhooks can be verified at all — false when no secret was configured. */
    public boolean verifiesWebhooks() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    private static AuthMode detect(final String key) {
        if (key.startsWith(LIVE_PREFIX) || key.startsWith(TEST_PREFIX)) {
            return AuthMode.API_KEY;
        }
        // -1 keeps the trailing empty segment, so "header.payload." counts as three parts exactly as
        // JavaScript's split('.') does. Matching the SDK matters more here than tightening it: a token
        // this refuses that the SDK accepts is an integration that works everywhere but here.
        if (key.indexOf('.') >= 0 && key.split("\\.", -1).length == 3) {
            return AuthMode.JWT;
        }
        return null;
    }
}
