package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

/** Whether a payment link still accepts buyers. {@link #UNKNOWN} carries anything else the server says. */
public enum PaymentLinkStatus {

    /** Live; a buyer opening it can pay. */
    ACTIVE,

    /** Withdrawn; the link no longer takes payments. */
    CANCELLED,

    /** AgentaOS reported a status this adapter does not model. */
    UNKNOWN;

    /** The wire spelling, or {@code null} for {@link #UNKNOWN}. */
    public String wireValue() {
        return this == UNKNOWN ? null : name().toLowerCase(Locale.ROOT);
    }

    /** The constant for a wire value, {@link #UNKNOWN} for anything unrecognised or absent. */
    public static PaymentLinkStatus fromWire(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (final PaymentLinkStatus status : values()) {
            if (status != UNKNOWN && status.wireValue().equals(value.toLowerCase(Locale.ROOT))) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
