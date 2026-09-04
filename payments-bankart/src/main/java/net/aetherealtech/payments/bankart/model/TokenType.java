package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Which stored token a deregister removes. The gateway defaults to {@link #ALL}. */
public enum TokenType {

    ALL, PAN, NT;

    @JsonValue
    public String wireValue() {
        return name();
    }
}
