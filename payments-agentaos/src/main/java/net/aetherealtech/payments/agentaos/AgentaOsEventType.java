package net.aetherealtech.payments.agentaos;

import net.aetherealtech.payments.Provisional;

/**
 * The webhook events AgentaOS sends.
 *
 * <p>Eight of them, of which the published README documents three. The five {@code subscription.*}
 * constants exist only in the SDK's {@code types.ts}, which is a statement about what the client is
 * prepared to receive rather than a promise about what the server sends — each is marked
 * {@link Provisional} so that "we never see subscription webhooks" is diagnosed as a possibly-wrong
 * assumption rather than as a bug in the handling.
 *
 * <p>Note the wire spelling {@code subscription.canceled}: one l, the American form, which is Stripe's
 * and therefore AgentaOS's. This SPI spells the same concept {@code CANCELLED} elsewhere; both spellings
 * are correct in their own vocabulary and neither is a typo to tidy up.
 */
public enum AgentaOsEventType {

    /** A buyer finished a hosted checkout and it is paid. */
    CHECKOUT_SESSION_COMPLETED("checkout.session.completed"),

    /** An outbound wallet transfer landed on chain. Not a payment to this merchant. */
    SEND_COMPLETED("send.completed"),

    /** An outbound wallet transfer failed. Not a payment to this merchant. */
    SEND_FAILED("send.failed"),

    /** A subscription now exists, because a buyer paid a subscription payment link. */
    @Provisional("Declared in packages/pay/src/types.ts's WebhookEvent union; packages/pay/README.md's "
            + "webhook table documents only checkout.session.completed, send.completed and send.failed.")
    SUBSCRIPTION_CREATED("subscription.created"),

    /** A subscription rolled into a new paid period. */
    @Provisional("Declared in packages/pay/src/types.ts's WebhookEvent union; packages/pay/README.md's "
            + "webhook table documents only checkout.session.completed, send.completed and send.failed.")
    SUBSCRIPTION_RENEWED("subscription.renewed"),

    /** A renewal charge did not go through. */
    @Provisional("Declared in packages/pay/src/types.ts's WebhookEvent union; packages/pay/README.md's "
            + "webhook table documents only checkout.session.completed, send.completed and send.failed.")
    SUBSCRIPTION_PAYMENT_FAILED("subscription.payment_failed"),

    /** Something about a subscription changed; what, the payload does not say directly. */
    @Provisional("Declared in packages/pay/src/types.ts's WebhookEvent union; packages/pay/README.md's "
            + "webhook table documents only checkout.session.completed, send.completed and send.failed.")
    SUBSCRIPTION_UPDATED("subscription.updated"),

    /** A subscription was cancelled — note the single-l wire spelling. */
    @Provisional("Declared in packages/pay/src/types.ts's WebhookEvent union; packages/pay/README.md's "
            + "webhook table documents only checkout.session.completed, send.completed and send.failed.")
    SUBSCRIPTION_CANCELED("subscription.canceled"),

    /** AgentaOS sent a type this adapter does not model. */
    UNKNOWN(null);

    private final String wireValue;

    AgentaOsEventType(final String wireValue) {
        this.wireValue = wireValue;
    }

    /** The {@code type} field's value, or null for {@link #UNKNOWN}. */
    public String wireValue() {
        return wireValue;
    }

    /** The constant for a wire value, {@link #UNKNOWN} for anything unrecognised or absent. */
    public static AgentaOsEventType fromWire(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (final AgentaOsEventType type : values()) {
            if (type != UNKNOWN && type.wireValue.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
