package net.aetherealtech.payments.bankart;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.exception.BankartApiException;
import net.aetherealtech.payments.bankart.exception.BankartException;
import net.aetherealtech.payments.bankart.exception.BankartTransactionException;
import net.aetherealtech.payments.bankart.exception.BankartTransportException;
import net.aetherealtech.payments.bankart.internal.Json;
import net.aetherealtech.payments.bankart.model.CaptureRequest;
import net.aetherealtech.payments.bankart.model.ContinueScheduleRequest;
import net.aetherealtech.payments.bankart.model.DeregisterRequest;
import net.aetherealtech.payments.bankart.model.IncrementalAuthorizationRequest;
import net.aetherealtech.payments.bankart.model.OptionsRequest;
import net.aetherealtech.payments.bankart.model.OptionsResponse;
import net.aetherealtech.payments.bankart.model.PaymentRequest;
import net.aetherealtech.payments.bankart.model.PayoutRequest;
import net.aetherealtech.payments.bankart.model.RedirectResult;
import net.aetherealtech.payments.bankart.model.RefundRequest;
import net.aetherealtech.payments.bankart.model.RegisterRequest;
import net.aetherealtech.payments.bankart.model.ScheduleResponse;
import net.aetherealtech.payments.bankart.model.StartScheduleRequest;
import net.aetherealtech.payments.bankart.model.StatusResponse;
import net.aetherealtech.payments.bankart.model.TransactionResponse;
import net.aetherealtech.payments.bankart.model.UpdateScheduleRequest;
import net.aetherealtech.payments.bankart.model.VoidRequest;
import net.aetherealtech.payments.bankart.signing.HmacSigner;

/**
 * A client for one Bankart gateway connector.
 *
 * <p>Thread-safe and intended to be built once and shared; it holds a {@link HttpClient} and a
 * mapper, both of which are expensive to create and safe to reuse.
 *
 * <h2>What throws and what does not</h2>
 * A declined card is an outcome, not a failure of the call: the operation methods return a
 * {@link TransactionResponse} with {@code returnType = ERROR} and let the caller decide. What does
 * throw is everything the caller got wrong or cannot proceed past — a rejected signature, invalid
 * request data, a transport failure ({@link BankartTransportException}), or a convenience method
 * whose promised shape did not arrive ({@link #startCheckout}).
 */
public final class BankartClient {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    /**
     * Pause and cancel declare a request body whose schema permits no properties at all
     * ({@code maxProperties: 0}), and mark it required. An empty object is the only body that
     * satisfies both; sending no body would fail the "required" half.
     */
    private static final String EMPTY_BODY = "{}";

    private final BankartConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final HmacSigner signer;
    private final Clock clock;

    public BankartClient(BankartConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Json.mapper(), Clock.systemUTC());
    }

    public BankartClient(BankartConfig config, HttpClient httpClient, ObjectMapper mapper, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.signer = config.signsRequests() ? new HmacSigner(config.sharedSecret()) : null;
    }

    public BankartConfig config() {
        return config;
    }

    // ---------------------------------------------------------------- transactions

    /** A complete customer-to-merchant payment. */
    public TransactionResponse debit(PaymentRequest request) {
        if (request.captureInMinutes() != null) {
            throw new IllegalArgumentException("captureInMinutes applies to preauthorize, not debit");
        }
        return transaction("debit", request);
    }

    /** Reserves the amount on the customer's instrument, to be completed by {@link #capture}. */
    public TransactionResponse preauthorize(PaymentRequest request) {
        return transaction("preauthorize", request);
    }

    public TransactionResponse capture(CaptureRequest request) {
        return transaction("capture", request);
    }

    public TransactionResponse voidTransaction(VoidRequest request) {
        return transaction("void", request);
    }

    public TransactionResponse refund(RefundRequest request) {
        return transaction("refund", request);
    }

    /** Stores an instrument for later use without charging it. */
    public TransactionResponse register(RegisterRequest request) {
        return transaction("register", request);
    }

    public TransactionResponse deregister(DeregisterRequest request) {
        return transaction("deregister", request);
    }

    public TransactionResponse payout(PayoutRequest request) {
        return transaction("payout", request);
    }

    /**
     * Raises or prolongs an authorization taken earlier by {@link #preauthorize}.
     *
     * <p>The request's amount is the increment, not the new total.
     */
    public TransactionResponse incrementalAuthorization(IncrementalAuthorizationRequest request) {
        return transaction("incrementalAuthorization", request);
    }

    // ---------------------------------------------------------------- schedules

    /**
     * Starts a schedule against an instrument registered by an earlier register,
     * debit-with-register or preauthorize-with-register.
     *
     * <p>The alternative is to start it inline, by attaching a
     * {@link net.aetherealtech.payments.bankart.model.Schedule} to that transaction in the first
     * place.
     */
    public ScheduleResponse startSchedule(StartScheduleRequest request) {
        return schedule("/schedule/" + config.apiKey() + "/start", serialize(request));
    }

    /** Changes a running schedule in place — price, cadence or registration. */
    public ScheduleResponse updateSchedule(String scheduleId, UpdateScheduleRequest request) {
        return schedule(schedulePath(scheduleId, "update"), serialize(request));
    }

    /** The schedule as the gateway sees it now, which is how a subscription is reconciled. */
    public ScheduleResponse showSchedule(String scheduleId) {
        URI uri = resolve(schedulePath(scheduleId, "get"));
        return convert(send(get(uri), uri), ScheduleResponse.class);
    }

    /** Stops billing without ending the schedule; {@link #continueSchedule} resumes it. */
    public ScheduleResponse pauseSchedule(String scheduleId) {
        return schedule(schedulePath(scheduleId, "pause"), EMPTY_BODY);
    }

    /** Resumes a paused schedule on the date the request names. */
    public ScheduleResponse continueSchedule(String scheduleId, ContinueScheduleRequest request) {
        return schedule(schedulePath(scheduleId, "continue"), serialize(request));
    }

    /**
     * Ends a schedule.
     *
     * <p>It takes effect at once and there is no deferred form: no field anywhere in the API asks
     * for a cancellation at the end of the paid period.
     */
    public ScheduleResponse cancelSchedule(String scheduleId) {
        return schedule(schedulePath(scheduleId, "cancel"), EMPTY_BODY);
    }

    // ---------------------------------------------------------------- options

    /**
     * An adapter's published list — the banks behind an online-banking method, typically.
     *
     * <p>{@code optionsName} is a path segment rather than a body field: it names the adapter's list,
     * and the request body carries only that adapter's own parameters.
     *
     * <p>This is the one operation in the API that requires no authentication. Basic auth and the
     * signature are sent anyway, since a gateway that ignores credentials it did not ask for costs
     * nothing and one connector's configuration is not a reason to build a second request path.
     */
    public OptionsResponse options(String optionsName, OptionsRequest request) {
        URI uri = resolve("/options/" + config.apiKey() + "/" + encodePathSegment(optionsName));
        return convert(send(post(uri, serialize(request)), uri), OptionsResponse.class);
    }

    // ---------------------------------------------------------------- checkout

    /**
     * Starts a hosted-page checkout and insists on getting somewhere to send the customer.
     *
     * <p>Persist the returned {@code uuid} against your order before redirecting. The notification
     * that decides the payment arrives independently of the customer's browser, and may arrive
     * first.
     *
     * @throws BankartTransactionException if the gateway declined the transaction outright
     * @throws BankartException            if it answered with something other than a redirect —
     *                                     a connector configured for server-to-server use, typically
     */
    public RedirectResult startCheckout(PaymentRequest request) {
        return toRedirect(debit(request));
    }

    /** The same, for storing an instrument through the hosted page rather than charging it. */
    public RedirectResult startRegistration(RegisterRequest request) {
        return toRedirect(register(request));
    }

    private RedirectResult toRedirect(TransactionResponse response) {
        if (response.isError()) {
            throw new BankartTransactionException(response);
        }
        if (!response.isRedirect() || response.redirectUrl() == null) {
            throw new BankartException(
                    "Expected a REDIRECT for a hosted checkout but the gateway answered " + response.returnType());
        }
        return RedirectResult.from(response);
    }

    // ---------------------------------------------------------------- status

    /**
     * The recovery path after a timeout: a transaction may exist even though no response reached
     * you, and this settles it without risking a second charge.
     *
     * <p><strong>Rate limited to 5 requests per minute per {@code uuid}</strong>, after which the
     * gateway answers HTTP 429 — so this is a recovery path and not a polling loop. Budget the five
     * for the cases that matter: a timed-out call, a customer asking, an operator investigating.
     */
    public StatusResponse statusByUuid(String uuid) {
        return status("getByUuid", uuid);
    }

    /**
     * The same lookup, keyed on the identifier YOU chose — which is what makes it usable after a
     * timeout, when no gateway UUID ever reached you.
     *
     * <p><strong>Rate limited to 5 requests per minute per {@code merchantTransactionId}</strong>,
     * after which the gateway answers HTTP 429. The budget is per identifier, so retrying the same
     * lookup in a tight loop exhausts it for that one transaction and nothing else.
     */
    public StatusResponse statusByMerchantTransactionId(String merchantTransactionId) {
        return status("getByMerchantTransactionId", merchantTransactionId);
    }

    // ---------------------------------------------------------------- plumbing

    private TransactionResponse transaction(String operation, Object request) {
        URI uri = resolve("/transaction/" + config.apiKey() + "/" + operation);
        String body = serialize(request);
        JsonNode json = send(post(uri, body), uri);
        raiseIfGeneralError(json);
        return convert(json, TransactionResponse.class);
    }

    private StatusResponse status(String operation, String identifier) {
        URI uri = resolve("/status/" + config.apiKey() + "/" + operation + "/" + encodePathSegment(identifier));
        JsonNode json = send(get(uri), uri);
        raiseIfGeneralError(json);
        return convert(json, StatusResponse.class);
    }

    /**
     * A schedule failure is NOT raised as a general error, unlike a transaction's. Its documented
     * failure body is a {@code ScheduleResponse} carrying {@code oldStatus} and {@code newStatus}
     * alongside the code — "the status of the schedule is not valid for the requested operation"
     * (7070) is only actionable if the caller can see what the status actually is, and throwing
     * would discard exactly that.
     */
    private ScheduleResponse schedule(String path, String body) {
        URI uri = resolve(path);
        return convert(send(post(uri, body), uri), ScheduleResponse.class);
    }

    private String schedulePath(String scheduleId, String operation) {
        return "/schedule/" + config.apiKey() + "/" + encodePathSegment(scheduleId) + "/" + operation;
    }

    private HttpRequest post(URI uri, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("Content-Type", CONTENT_TYPE)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        sign(builder, "POST", body.getBytes(StandardCharsets.UTF_8), uri);
        return builder.build();
    }

    private HttpRequest get(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("Content-Type", CONTENT_TYPE)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth())
                .GET();
        // The docs describe signing only for the transaction API's POSTs. A GET has no body, so the
        // body component is the hash of zero bytes; the Content-Type is sent (unusual on a GET) so
        // that both sides have the same string to sign.
        sign(builder, "GET", new byte[0], uri);
        return builder.build();
    }

    private void sign(HttpRequest.Builder builder, String method, byte[] body, URI uri) {
        if (signer == null) {
            return;
        }
        String date = HmacSigner.formatDate(clock.instant());
        String signature = signer.sign(method, body, CONTENT_TYPE, date, signedUri(uri));
        // Both headers carry the same value. Date is what the docs require and java.net.http does
        // permit setting it; X-Date is sent as well because the docs give it precedence, so if
        // anything between here and the gateway rewrites Date, the signed value still arrives.
        builder.header("Date", date);
        builder.header("X-Date", date);
        builder.header("X-Signature", signature);
    }

    /** The path the gateway signs — with the API key substituted, and the query string if any. */
    private static String signedUri(URI uri) {
        String query = uri.getRawQuery();
        return query == null ? uri.getRawPath() : uri.getRawPath() + "?" + query;
    }

    private JsonNode send(HttpRequest request, URI uri) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BankartTransportException("Request to " + uri + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BankartTransportException("Request to " + uri + " was interrupted", e);
        }

        String body = response.body();
        try {
            JsonNode json = mapper.readTree(body == null ? "" : body);
            if (json == null || json.isMissingNode() || !json.isObject()) {
                throw new BankartApiException("Gateway returned a non-JSON body", 0, response.statusCode(), body);
            }
            return json;
        } catch (IOException e) {
            throw new BankartApiException(
                    "Gateway returned an unparseable body (HTTP " + response.statusCode() + ")",
                    0, response.statusCode(), body);
        }
    }

    /**
     * The general error shape — {@code {"success": false, "errorMessage": ..., "errorCode": ...}} —
     * is distinguished from a failed transaction by having no {@code returnType}. Authentication and
     * signature rejections arrive this way, and no amount of retrying will change them.
     */
    private void raiseIfGeneralError(JsonNode json) {
        if (json.hasNonNull("returnType")) {
            return;
        }
        boolean succeeded = json.path("success").asBoolean(false);
        if (!succeeded && json.hasNonNull("errorCode")) {
            throw new BankartApiException(
                    json.path("errorMessage").asText("Gateway rejected the request"),
                    json.path("errorCode").asInt(),
                    200,
                    json.toString());
        }
    }

    private <T> T convert(JsonNode json, Class<T> type) {
        try {
            return mapper.treeToValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BankartApiException("Could not read the gateway's response", 0, 200, json.toString());
        }
    }

    private String serialize(Object request) {
        try {
            return mapper.writeValueAsString(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BankartException("Could not serialise the request", e);
        }
    }

    private URI resolve(String path) {
        return URI.create(config.baseUrl() + path);
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basicAuth() {
        String credentials = config.username() + ":" + config.password();
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
