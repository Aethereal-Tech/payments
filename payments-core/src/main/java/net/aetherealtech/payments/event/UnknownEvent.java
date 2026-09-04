package net.aetherealtech.payments.event;

import java.util.Objects;

/**
 * A webhook that authenticated but says something this SPI has no event for.
 *
 * <p>The alternative designs are both worse. Throwing would refuse a request the provider will then
 * retry for days, on a signature that was perfectly valid. Mapping it to the nearest-looking event would
 * put a guess into a consumer's ledger. This says exactly what is true: the provider is telling us
 * something, it is genuinely from them, and we do not know what it means.
 *
 * <p>{@link #providerEventType()} is the provider's own type string where the payload carried one, which
 * is what makes a monitoring alert actionable — "agentaos sent invoice.payment_failed 40 times today" is
 * a work item, "40 unknown events" is not. The payload itself is on
 * {@link EventHeader#rawPayload()}, as it is for every event.
 *
 * <p>Still acknowledge it. {@link #acknowledgement()} carries the body the provider expects, and
 * refusing to acknowledge an event we merely do not model would stall the provider's whole queue for
 * this endpoint.
 *
 * @param header            identity, timing and acknowledgement — and the raw payload
 * @param providerEventType the provider's own type string, or null when the payload carried none
 */
public record UnknownEvent(EventHeader header, String providerEventType) implements PaymentEvent {

    public UnknownEvent {
        Objects.requireNonNull(header, "header must not be null");
    }

    public static UnknownEvent of(final EventHeader header, final String providerEventType) {
        return new UnknownEvent(header, providerEventType);
    }
}
