package net.aetherealtech.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Whether 3-D Secure authentication is attempted for a transaction. */
public enum ThreeDSecureMode {

    OFF, OPTIONAL, MANDATORY, UNKNOWN;

    @JsonCreator
    public static ThreeDSecureMode fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ThreeDSecureMode mode : values()) {
            if (mode != UNKNOWN && mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return name();
    }
}
