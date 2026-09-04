package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How a {@link ReturnType#REDIRECT} is meant to be presented. */
public enum RedirectType {

    IFRAME("iframe"), FULLPAGE("fullpage"), THREE_DS("3ds"), UNKNOWN("");

    private final String wire;

    RedirectType(String wire) {
        this.wire = wire;
    }

    @JsonCreator
    public static RedirectType fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (RedirectType type : values()) {
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
