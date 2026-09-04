package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

/** The input control a {@link CheckoutField} renders as on the hosted page. */
public enum CheckoutFieldType {

    /** A free-text box. */
    TEXT,

    /** An email address, validated by the hosted page. */
    EMAIL,

    /** A telephone number. */
    TEL,

    /** A choice from {@link CheckoutField#options()}. */
    SELECT;

    /** The wire spelling. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The constant for a wire value, or null when absent or unrecognised. */
    public static CheckoutFieldType fromWire(final String value) {
        if (value == null) {
            return null;
        }
        final String normalised = value.toLowerCase(Locale.ROOT);
        for (final CheckoutFieldType type : values()) {
            if (type.wireValue().equals(normalised)) {
                return type;
            }
        }
        return null;
    }
}
