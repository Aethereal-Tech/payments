package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

/**
 * Where a checkout session got to.
 *
 * <p>{@link #UNKNOWN} is not one of the four the SDK declares. It is here because a closed union in a
 * TypeScript type is a statement about the client, not a promise from the server, and a status this
 * adapter cannot name is a fact to carry rather than an exception to throw at a caller reading a
 * session.
 */
public enum CheckoutStatus {

    /** Created and waiting for the buyer. */
    OPEN,

    /** Paid. */
    COMPLETED,

    /** The session's window ran out before it was paid. */
    EXPIRED,

    /** Cancelled, by the merchant or by AgentaOS. */
    CANCELLED,

    /** AgentaOS reported a status this adapter does not model. */
    UNKNOWN;

    /** The wire spelling, or {@code null} for {@link #UNKNOWN}, which never travels outbound. */
    public String wireValue() {
        return this == UNKNOWN ? null : name().toLowerCase(Locale.ROOT);
    }

    /** The constant for a wire value, {@link #UNKNOWN} for anything unrecognised or absent. */
    public static CheckoutStatus fromWire(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (final CheckoutStatus status : values()) {
            if (status != UNKNOWN && status.wireValue().equals(value.toLowerCase(Locale.ROOT))) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
