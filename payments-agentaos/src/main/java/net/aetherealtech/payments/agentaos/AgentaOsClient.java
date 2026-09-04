package net.aetherealtech.payments.agentaos;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.aetherealtech.payments.agentaos.internal.Json;
import net.aetherealtech.payments.agentaos.model.CancelSubscriptionResult;
import net.aetherealtech.payments.agentaos.model.Checkout;
import net.aetherealtech.payments.agentaos.model.CreateCheckoutRequest;
import net.aetherealtech.payments.agentaos.model.CreatePaymentLinkRequest;
import net.aetherealtech.payments.agentaos.model.Customer;
import net.aetherealtech.payments.agentaos.model.Page;
import net.aetherealtech.payments.agentaos.model.PaymentLink;
import net.aetherealtech.payments.agentaos.model.Subscription;
import net.aetherealtech.payments.exception.PaymentProviderException;

/**
 * A client for one AgentaOS organization's gateway API.
 *
 * <p>Thread-safe; build one and share it. It holds an {@link HttpClient}, which is expensive to create
 * and safe to reuse.
 *
 * <h2>Two spellings, and they are not the same one</h2>
 *
 * <p><strong>Request bodies are camelCase. Response bodies are snake_case.</strong> That is the server's
 * own asymmetry — its DTOs are camelCase and its serialiser snake-cases what it returns — and it is the
 * single easiest thing about this API to get wrong, in either direction. The model records read
 * snake_case; the request records write camelCase; neither transforms the other's convention.
 *
 * <h2>It does not retry</h2>
 *
 * <p>The SDK retries a 5xx and a network failure with exponential backoff, and this deliberately does
 * not. Every POST here creates something — a session, a link, a cancellation — and a library that
 * silently repeats a POST whose answer never arrived is a library that double-charges. A failure whose
 * outcome is genuinely unknown is raised with
 * {@link PaymentProviderException#outcomeUnknown()} true, and the recovery is to ASK what happened, not
 * to send it again.
 */
public final class AgentaOsClient {

    private static final String GATEWAY = "/api/v1/gateway";
    private static final String SESSIONS = GATEWAY + "/sessions";
    private static final String PAYMENT_LINKS = GATEWAY + "/payment-links";
    private static final String SUBSCRIPTIONS = GATEWAY + "/subscriptions";
    private static final String CUSTOMERS = GATEWAY + "/customers";

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final AgentaOsConfig config;
    private final HttpClient httpClient;

    public AgentaOsClient(final AgentaOsConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public AgentaOsClient(final AgentaOsConfig config, final HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /** The configuration this client was built with. */
    public AgentaOsConfig config() {
        return config;
    }

    // ---------------------------------------------------------------- checkouts

    /** Opens a checkout session, with a fresh idempotency key. */
    public Checkout createCheckout(final CreateCheckoutRequest request) {
        return createCheckout(request, null);
    }

    /**
     * Opens a checkout session under a caller-chosen idempotency key.
     *
     * <p>Supply one when a checkout may be retried — a browser refresh on an order page, a queued job
     * that runs twice — so the retry resolves to the session the first attempt created rather than a
     * second one the buyer might also pay.
     */
    public Checkout createCheckout(final CreateCheckoutRequest request, final String idempotencyKey) {
        Objects.requireNonNull(request, "request must not be null");
        return Checkout.from(post(SESSIONS, Json.write(request.toWire()), idempotencyKey, "create a checkout"));
    }

    /** One checkout session by the {@code sessionId} its creation returned. */
    public Checkout retrieveCheckout(final String sessionId) {
        return Checkout.from(get(SESSIONS + "/" + segment(sessionId, "sessionId"), "retrieve a checkout"));
    }

    /** Cancels an open checkout session. */
    public Checkout cancelCheckout(final String sessionId) {
        final String path = SESSIONS + "/" + segment(sessionId, "sessionId") + "/cancel";
        return Checkout.from(post(path, null, null, "cancel a checkout"));
    }

    // ---------------------------------------------------------------- payment links

    /** Creates a payment link, with a fresh idempotency key. */
    public PaymentLink createPaymentLink(final CreatePaymentLinkRequest request) {
        return createPaymentLink(request, null);
    }

    /** Creates a payment link under a caller-chosen idempotency key. */
    public PaymentLink createPaymentLink(final CreatePaymentLinkRequest request, final String idempotencyKey) {
        Objects.requireNonNull(request, "request must not be null");
        return PaymentLink.from(
                post(PAYMENT_LINKS, Json.write(request.toWire()), idempotencyKey, "create a payment link"));
    }

    /** One payment link by id. */
    public PaymentLink retrievePaymentLink(final String linkId) {
        return PaymentLink.from(
                get(PAYMENT_LINKS + "/" + segment(linkId, "linkId"), "retrieve a payment link"));
    }

    /**
     * Withdraws a payment link.
     *
     * <p>A DELETE, where cancelling a CHECKOUT is a POST to a {@code /cancel} sub-path. The two are not
     * spelled the same way and there is no pattern to infer from one to the other.
     */
    public PaymentLink cancelPaymentLink(final String linkId) {
        return PaymentLink.from(
                delete(PAYMENT_LINKS + "/" + segment(linkId, "linkId"), "cancel a payment link"));
    }

    // ---------------------------------------------------------------- subscriptions

    /** One page of subscriptions. There is no endpoint that retrieves a single one. */
    public Page<Subscription> listSubscriptions(final int limit, final int offset) {
        return Page.from(get(SUBSCRIPTIONS + pageQuery(limit, offset), "list subscriptions"), Subscription::from);
    }

    /**
     * Cancels a subscription, now or when the paid period runs out.
     *
     * <p>{@code atPeriodEnd} is sent explicitly either way. The SDK defaults it to true when the caller
     * says nothing, and a default that decides whether somebody keeps the access they paid for is not
     * one to inherit silently.
     */
    public CancelSubscriptionResult cancelSubscription(final String subscriptionId, final boolean atPeriodEnd) {
        final String path = SUBSCRIPTIONS + "/" + segment(subscriptionId, "subscriptionId") + "/cancel";
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("atPeriodEnd", atPeriodEnd);
        final String body = Json.write(fields);
        return CancelSubscriptionResult.from(post(path, body, null, "cancel a subscription"));
    }

    // ---------------------------------------------------------------- customers

    /** One page of the buyers who have paid this organization. */
    public Page<Customer> listCustomers(final int limit, final int offset) {
        return Page.from(get(CUSTOMERS + pageQuery(limit, offset), "list customers"), Customer::from);
    }

    // ---------------------------------------------------------------- transport

    private Map<String, Object> get(final String path, final String what) {
        return send(base(path).GET().build(), what);
    }

    private Map<String, Object> delete(final String path, final String what) {
        return send(base(path).DELETE().build(), what);
    }

    private Map<String, Object> post(
            final String path, final String body, final String idempotencyKey, final String what) {
        final HttpRequest.Builder request = base(path)
                // Every POST carries one, generated when the caller supplied none. A retried checkout
                // that creates a second payable session is the failure this header exists to prevent.
                .header("idempotency-key", idempotencyKey == null || idempotencyKey.isBlank()
                        ? UUID.randomUUID().toString()
                        : idempotencyKey);
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            // content-type is set only where there is a body: the SDK sets it on nothing else, and a
            // GET carrying one is a difference from what the server has been tested against.
            request.header("content-type", JSON_CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return send(request.build(), what);
    }

    private HttpRequest.Builder base(final String path) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl().toString() + path))
                .timeout(config.timeout())
                .header("accept", JSON_CONTENT_TYPE)
                .header(config.authMode().headerName(), config.authHeaderValue());
    }

    private Map<String, Object> send(final HttpRequest request, final String what) {
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw unknownOutcome("timed out trying to " + what, "timeout_error", e);
        } catch (IOException e) {
            throw unknownOutcome("could not reach AgentaOS to " + what, "network_error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unknownOutcome("was interrupted trying to " + what, "interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure(response, what);
        }
        final String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return Json.parseObject(body);
        } catch (Json.SyntaxException e) {
            throw new PaymentProviderException(
                    "AgentaOS answered the request to " + what + " with a body that is not a JSON object: "
                            + e.getMessage(),
                    AgentaOsPaymentProvider.PROVIDER_ID, "malformed_response", false, e);
        }
    }

    private static PaymentProviderException unknownOutcome(
            final String what, final String code, final Throwable cause) {
        return new PaymentProviderException(
                "AgentaOS " + what + ". The request may still have been processed — ask what happened rather"
                        + " than sending it again.",
                AgentaOsPaymentProvider.PROVIDER_ID, code, true, cause);
    }

    private PaymentProviderException failure(final HttpResponse<String> response, final String what) {
        final int status = response.statusCode();
        final Map<String, Object> body = errorBody(response.body());
        String message = Json.string(body, "message");
        if (message == null) {
            // The server's own words, then its "error" field, then ours. The SDK replaces a 404's message
            // with a fixed sentence of its own; keeping what the server said loses nothing and can name
            // the resource it could not find.
            message = Json.string(body, "error");
        }
        if (message == null || message.isBlank()) {
            message = "AgentaOS refused the request to " + what + " with HTTP " + status;
        }
        final StringBuilder detail = new StringBuilder(message);
        if (status == 400) {
            Json.objects(body, "errors").forEach(error -> detail
                    .append(" [")
                    .append(Json.string(error, "field"))
                    .append(": ")
                    .append(Json.string(error, "message"))
                    .append(']'));
        }
        if (status == 429) {
            response.headers().firstValue("retry-after").ifPresent(seconds -> detail
                    .append(" (retry after ")
                    .append(seconds)
                    .append("s)"));
        }
        final String requestId = response.headers().firstValue("x-request-id").orElse(null);
        if (requestId != null) {
            detail.append(" (request ").append(requestId).append(')');
        }
        return new PaymentProviderException(
                detail.toString(), AgentaOsPaymentProvider.PROVIDER_ID, codeFor(status), false, null);
    }

    private static Map<String, Object> errorBody(final String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return Json.parseObject(body);
        } catch (Json.SyntaxException e) {
            // An error body that is not JSON is a proxy's HTML or an empty 502; the status still maps.
            return Map.of();
        }
    }

    private static String codeFor(final int status) {
        return switch (status) {
            case 400 -> "validation_error";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found";
            case 409 -> "idempotency_error";
            case 429 -> "rate_limit";
            default -> status >= 500 ? "api_error" : "unknown_error";
        };
    }

    private static String pageQuery(final int limit, final int offset) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100, got " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative, got " + offset);
        }
        return "?limit=" + limit + "&offset=" + offset;
    }

    private static String segment(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        // Percent-encoded, not concatenated: an identifier carrying a slash or a question mark would
        // otherwise change which endpoint is called rather than which resource.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
