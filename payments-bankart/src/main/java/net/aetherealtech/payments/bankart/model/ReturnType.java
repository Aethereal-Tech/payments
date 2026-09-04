package net.aetherealtech.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What the caller must do next with a transaction response.
 *
 * <p>{@link #UNKNOWN} exists because the docs reserve the right to add enum values and require
 * integrators to cope: a value we have never heard of must not become a parse failure in the middle
 * of a payment.
 */
public enum ReturnType {

    FINISHED, REDIRECT, HTML, PENDING, ERROR, UNKNOWN;

    @JsonCreator
    public static ReturnType fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ReturnType type : values()) {
            if (type != UNKNOWN && type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return name();
    }
}
