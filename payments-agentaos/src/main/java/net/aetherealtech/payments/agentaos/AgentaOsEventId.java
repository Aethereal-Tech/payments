package net.aetherealtech.payments.agentaos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.event.EventHeader;

/**
 * The identifier this adapter puts on an AgentaOS event, because AgentaOS puts none on it itself.
 *
 * <p>{@link EventHeader#eventId()} is what a consumer keys its idempotency on, so it cannot be absent
 * and it cannot be random — a fresh id per delivery would turn every redelivery into a second event and
 * a subscriber into two. AgentaOS's payloads carry no event id, so one is derived:
 *
 * <pre>
 * eventId = "agentaos_" + first 32 hex characters of
 *           SHA-256( type + "\n" + resourceId + "\n" + signatureTimestamp )
 * </pre>
 *
 * where {@code type} is the payload's own {@code type} string, {@code resourceId} is the identifier of
 * the thing the event is about — the session id for a checkout, the transaction id for a send, the
 * subscription id for a {@code subscription.*} — and {@code signatureTimestamp} is the {@code t=}
 * element of the {@code x-agentaos-signature} header, in seconds.
 *
 * <p>Those three fields, and not the body, because two genuinely different events can share a body: a
 * subscription cancelled and re-cancelled sends the same JSON twice. And not a counter or a clock read,
 * because either would make the same delivery a different event on each attempt.
 *
 * <p><strong>What this rests on:</strong> that a redelivery re-sends the timestamp it was first signed
 * at rather than signing afresh. If AgentaOS re-signs, ids will differ between attempts and a consumer
 * must dedupe on the payload instead. Nothing in the SDK says which, and only a real account can settle
 * it — which is why the whole class is {@link Provisional}.
 */
@Provisional("AgentaOS webhook payloads carry no event id; this identifier is this library's own "
        + "construction, and its stability across a redelivery assumes AgentaOS re-sends the signature "
        + "timestamp it first signed with, which nothing in packages/pay/src states either way.")
public final class AgentaOsEventId {

    private static final String PREFIX = "agentaos_";
    private static final int HEX_LENGTH = 32;

    private AgentaOsEventId() {
    }

    /**
     * The identifier for one delivery.
     *
     * @param type               the payload's {@code type} string, or null when it carried none
     * @param resourceId         the id of the thing the event is about, or null when none was found
     * @param signatureTimestamp the {@code t=} element of the signature header, in seconds
     */
    public static String of(final String type, final String resourceId, final long signatureTimestamp) {
        final String material = (type == null ? "" : type)
                + "\n" + (resourceId == null ? "" : resourceId)
                + "\n" + signatureTimestamp;
        return PREFIX + sha256Hex(material).substring(0, HEX_LENGTH);
    }

    private static String sha256Hex(final String material) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE; its absence is a broken installation, not bad input.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
