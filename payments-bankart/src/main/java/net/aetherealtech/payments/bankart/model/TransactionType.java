package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of transaction a notification or status lookup describes.
 *
 * <p>Matched case-insensitively: the status API's own examples show both {@code "debit"} and
 * {@code "DEBIT"} for the same field.
 *
 * <p>The last three exist in the OpenAPI enum but not in the prose documentation's callback table,
 * which stops at {@code PAYOUT}. They are modelled from the machine-readable source, since a value
 * that arrives and degrades to {@link #UNKNOWN} is a callback nobody can act on.
 */
public enum TransactionType {

    DEBIT("DEBIT"),
    PREAUTHORIZE("PREAUTHORIZE"),
    CAPTURE("CAPTURE"),
    VOID("VOID"),
    REFUND("REFUND"),
    PAYOUT("PAYOUT"),
    REGISTER("REGISTER"),
    DEREGISTER("DEREGISTER"),
    CHARGEBACK("CHARGEBACK"),
    CHARGEBACK_REVERSAL("CHARGEBACK-REVERSAL"),
    INCREMENTAL_AUTHORIZATION("INCREMENTAL-AUTHORIZATION"),
    DISPUTE("DISPUTE"),
    DISPUTE_REVERSAL("DISPUTE-REVERSAL"),
    UNKNOWN("");

    private final String wire;

    TransactionType(String wire) {
        this.wire = wire;
    }

    @JsonCreator
    public static TransactionType fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (TransactionType type : values()) {
            if (type != UNKNOWN && type.wire.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return wire;
    }
}
