package net.aetherealtech.payments.bankart.signing;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The five signed components of one HTTP request.
 *
 * <p>The same shape serves both directions: outbound, this library fills it in; inbound, a merchant
 * fills it in from the notification request it received, which is exactly what the docs prescribe
 * ("instead of defining the various values ... you must take them out of the HTTP request you
 * received from us").
 */
public record SignedRequest(String method, byte[] body, String contentType, String date, String requestUri) {

    public SignedRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(requestUri, "requestUri");
        body = body.clone();
    }

    public static SignedRequest post(String body, String contentType, String date, String requestUri) {
        return new SignedRequest("POST", body.getBytes(StandardCharsets.UTF_8), contentType, date, requestUri);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
