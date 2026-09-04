package net.aetherealtech.payments;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One inbound webhook request, in the pieces an adapter needs to authenticate it.
 *
 * <p>A whole request rather than a fixed list of named header fields, because the providers sign
 * different things. Bankart's signature covers the method, a hash of the body, the {@code Content-Type},
 * the {@code Date} and the request URI; AgentaOS's covers a timestamp and the body, and travels in one
 * header of its own. A record with four named slots would have needed a new slot for the second provider
 * and another for the third.
 *
 * <p><strong>The body must be the RAW bytes as received.</strong> Every signature scheme here covers the
 * exact octets the provider transmitted, so a body that has been parsed and re-serialised — which is
 * what a JSON body parser running before the handler produces — will not verify, however identical it
 * looks. This is the single most common way a working integration is reported as a broken one.
 *
 * @param method     the HTTP method, e.g. {@code POST}
 * @param body       the raw request body; defensively copied, so a caller reusing a buffer is safe
 * @param requestUri the path, plus the query string if the callback URL registered one — a
 *                   {@code callbackUrl} may carry query parameters, and Bankart signs what it sent
 * @param headers    the request headers; looked up case-insensitively by {@link #header(String)}
 */
public record InboundWebhook(String method, byte[] body, String requestUri, Map<String, String> headers) {

    public InboundWebhook {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(requestUri, "requestUri must not be null");
        body = body.clone();
        // HTTP header names are case-insensitive (RFC 9110 §5.1) and every layer between the provider
        // and the handler is free to re-case them. A plain Map lookup would then miss a header that is
        // present, and the failure would read as a missing signature — which is indistinguishable from
        // an attack, and would be handled as one.
        final Map<String, String> insensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            // Map.copyOf below refuses a null value outright, and header() already treats a null
            // value as absent — so a null-valued entry is dropped here rather than left to blow up
            // construction over a header the caller was never going to be able to read anyway.
            headers.forEach((name, value) -> {
                if (name != null && value != null) {
                    insensitive.put(name, value);
                }
            });
        }
        headers = Map.copyOf(insensitive);
    }

    /** A POST webhook, which is what every provider modelled here sends. */
    public static InboundWebhook post(final byte[] body, final String requestUri, final Map<String, String> headers) {
        return new InboundWebhook("POST", body, requestUri, headers);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /** The body decoded as UTF-8, for a scheme that signs text rather than octets. */
    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** One header by name, case-insensitively. Empty when absent; never null. */
    public Optional<String> header(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The body is deliberately NOT rendered. A webhook body carries customer data and this string
     * ends up in logs.
     */
    @Override
    public String toString() {
        return "InboundWebhook[method=" + method
                + ", requestUri=" + requestUri
                + ", bodyBytes=" + body.length
                + ", headers=" + headers.keySet().stream().map(n -> n.toLowerCase(Locale.ROOT)).toList()
                + "]";
    }
}
