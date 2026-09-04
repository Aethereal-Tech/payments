package net.aetherealtech.payments.event;

import java.time.Instant;
import java.util.Objects;

/**
 * What every {@link PaymentEvent} carries regardless of what it says.
 *
 * <p>Three of these four fields exist to make delivery survivable, because webhook delivery is neither
 * exactly-once nor ordered:
 *
 * <ul>
 *   <li><strong>{@code eventId}</strong> — the provider's own identifier for this delivery, stable
 *       across redeliveries. Key your idempotency on it. A provider that publishes no such id has one
 *       synthesised by its adapter from fields that identify the same event (documented per adapter),
 *       because a consumer cannot dedupe on a field that is sometimes absent.</li>
 *   <li><strong>{@code occurredAt}</strong> — when the fact happened, not when it was delivered.
 *       An event older than one you already applied is a late redelivery; let the newest win rather than
 *       rejecting it, and remember that a transaction legitimately moving from failed to successful is a
 *       documented Bankart behaviour, not a replay.</li>
 *   <li><strong>{@code acknowledgement}</strong> — the exact body to answer the request with. Providers
 *       differ: Bankart requires HTTP 200 with the literal body {@code OK} and retries on anything else
 *       for seven days; AgentaOS wants a 2xx and does not care about the body. It travels with the event
 *       so the handler writing the response does not have to know which provider it is serving.</li>
 * </ul>
 *
 * <p>{@code rawPayload} is the body exactly as received. It is on EVERY event, not only
 * {@link UnknownEvent}, because the first question about a mis-mapped event is always what the provider
 * actually sent — and because an operator who has to reach for the provider's dashboard to answer it
 * usually cannot, the payload having been discarded. It carries customer data: log it deliberately.
 *
 * @param eventId         the provider's identifier for this delivery; never blank
 * @param provider        which adapter produced this, matching
 *                        {@link net.aetherealtech.payments.PaymentProvider#id()}
 * @param occurredAt      when the fact happened, as the provider reported or the adapter observed it
 * @param acknowledgement the exact body to answer the webhook request with; never null, possibly empty
 * @param rawPayload      the webhook body exactly as received
 */
public record EventHeader(
        String eventId,
        String provider,
        Instant occurredAt,
        String acknowledgement,
        String rawPayload) {

    public EventHeader {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        acknowledgement = Objects.requireNonNullElse(acknowledgement, "");
        rawPayload = Objects.requireNonNullElse(rawPayload, "");
    }

    public static Builder builder(final String eventId, final String provider, final Instant occurredAt) {
        return new Builder(eventId, provider, occurredAt);
    }

    public static final class Builder {
        private final String eventId;
        private final String provider;
        private final Instant occurredAt;
        private String acknowledgement;
        private String rawPayload;

        private Builder(final String eventId, final String provider, final Instant occurredAt) {
            this.eventId = eventId;
            this.provider = provider;
            this.occurredAt = occurredAt;
        }

        public Builder acknowledgement(final String acknowledgement) {
            this.acknowledgement = acknowledgement;
            return this;
        }

        public Builder rawPayload(final String rawPayload) {
            this.rawPayload = rawPayload;
            return this;
        }

        public EventHeader build() {
            return new EventHeader(eventId, provider, occurredAt, acknowledgement, rawPayload);
        }
    }
}
