package net.aetherealtech.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of transaction a notification or status lookup describes.
 *
 * <p>Matched case-insensitively: the status API's own examples show both {@code "debit"} and
 * {@code "DEBIT"} for the same field.
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
