package net.aetherealtech.payments.bankart.notification;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.exception.BankartNotificationException;
import net.aetherealtech.payments.bankart.internal.Json;

/**
 * Turns a callback body into a {@link Notification}.
 *
 * <p>The payload is JSON. The integration guide calls it "a notification XML", but that wording
 * survives from the gateway's older XML API — the API Reference V3, which the guide itself points at
 * for the details, publishes JSON for every notification variant, and the same page distinguishes
 * {@code referenceTransactionId (XML)} from {@code referenceUuid (JSON)}. See the README's
 * "Open questions" section.
 *
 * <p>Answering the request is half the contract and it is not this class's job to do it for you:
 * respond HTTP 200 with the body {@link Notification#ACKNOWLEDGEMENT}, and do it only once the
 * notification is durably recorded. Acknowledging first and persisting after converts a crash into
 * a silently lost payment, because the gateway will not send it again.
 */
public final class NotificationParser {

    private final ObjectMapper mapper;

    public NotificationParser() {
        this(Json.mapper());
    }

    public NotificationParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public Notification parse(String body) {
        Objects.requireNonNull(body, "body");
        return parse(body.getBytes(StandardCharsets.UTF_8));
    }

    public Notification parse(byte[] body) {
        Objects.requireNonNull(body, "body");
        if (body.length == 0) {
            // Called out separately because Jackson's own message for this ("No content to map due
            // to end-of-input") sends the reader looking for a malformed payload that isn't there.
            throw new BankartNotificationException("Notification body was empty", null);
        }
        try {
            return mapper.readValue(body, Notification.class);
        } catch (Exception e) {
            throw new BankartNotificationException("Could not parse notification body", e);
        }
    }

    /** The exact body to write back, for callers who would rather not hard-code a string literal. */
    public static String acknowledgement() {
        return Notification.ACKNOWLEDGEMENT;
    }
}
