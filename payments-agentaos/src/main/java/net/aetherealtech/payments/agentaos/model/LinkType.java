package net.aetherealtech.payments.agentaos.model;

import java.util.Locale;

/**
 * Whether a payment link charges once or establishes a subscription.
 *
 * <p>This is the field that makes AgentaOS's subscriptions work the way they do: there is no API that
 * creates a subscription, so a {@link #SUBSCRIPTION} link is the only thing that can, and it does so
 * when a buyer pays it.
 */
public enum LinkType {

    /** One charge, and the link stays open for the next buyer. */
    ONE_TIME("one_time"),

    /** Paying it starts a subscription on the link's {@link BillingInterval}. */
    SUBSCRIPTION("subscription");

    private final String wireValue;

    LinkType(final String wireValue) {
        this.wireValue = wireValue;
    }

    /** The wire spelling. */
    public String wireValue() {
        return wireValue;
    }

    /** The constant for a wire value, or null when absent or unrecognised. */
    public static LinkType fromWire(final String value) {
        if (value == null) {
            return null;
        }
        final String normalised = value.toLowerCase(Locale.ROOT);
        for (final LinkType type : values()) {
            if (type.wireValue.equals(normalised)) {
                return type;
            }
        }
        return null;
    }
}
