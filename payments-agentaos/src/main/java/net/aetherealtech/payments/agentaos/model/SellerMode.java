package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

/**
 * How a sale settles: card and bank through AgentaOS as merchant of record, or on-chain to the
 * merchant's own wallet.
 *
 * <p>Read-only, always. It is derived by the server from the account or inherited from the payment link,
 * and the SDK asserts it is absent from every request body — sending it is how a 2.0.0 client gets a 400.
 */
public enum SellerMode {

    /** Merchant of record: AgentaOS takes the card or bank payment and settles to the merchant. */
    MOR,

    /** On-chain: the buyer pays the merchant's wallet directly. */
    CRYPTO,

    /** AgentaOS reported a mode this adapter does not model. */
    UNKNOWN;

    /** The wire spelling, or {@code null} for {@link #UNKNOWN}. */
    public String wireValue() {
        return this == UNKNOWN ? null : name().toLowerCase(Locale.ROOT);
    }

    /** The constant for a wire value, {@link #UNKNOWN} for anything unrecognised or absent. */
    public static SellerMode fromWire(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (final SellerMode mode : values()) {
            if (mode != UNKNOWN && mode.wireValue().equals(value.toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        return UNKNOWN;
    }
}
