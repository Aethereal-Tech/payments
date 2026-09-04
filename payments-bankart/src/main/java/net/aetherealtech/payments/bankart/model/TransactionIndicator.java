package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Declares, for card schemes, which card-on-file or recurring role a transaction plays.
 *
 * <p>The scheme rules make this load-bearing rather than informational: the wrong indicator on a
 * merchant-initiated charge is what gets a series declined.
 */
public enum TransactionIndicator {

    SINGLE("SINGLE"),
    INITIAL("INITIAL"),
    RECURRING("RECURRING"),
    CARDONFILE("CARDONFILE"),
    CARDONFILE_MERCHANT_INITIATED("CARDONFILE-MERCHANT-INITIATED"),
    MOTO("MOTO"),
    UNKNOWN("");

    private final String wire;

    TransactionIndicator(String wire) {
        this.wire = wire;
    }

    @JsonCreator
    public static TransactionIndicator fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (TransactionIndicator indicator : values()) {
            if (indicator != UNKNOWN && indicator.wire.equalsIgnoreCase(value)) {
                return indicator;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return wire;
    }
}
