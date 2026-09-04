package net.aetherealtech.payments.bankart.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code result} field of a status notification. */
public enum NotificationResult {

    OK, PENDING, ERROR, UNKNOWN;

    @JsonCreator
    public static NotificationResult fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (NotificationResult result : values()) {
            if (result != UNKNOWN && result.name().equalsIgnoreCase(value)) {
                return result;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return name();
    }
}
