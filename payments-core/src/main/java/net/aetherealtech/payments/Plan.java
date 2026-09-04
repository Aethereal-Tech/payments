package net.aetherealtech.payments;

import java.util.Objects;

/**
 * The thing a subscription is a subscription TO, named by the provider's own identifier.
 *
 * <p>{@code ref} is what {@link PaymentIntent#planRef()} would carry to start a checkout for this plan
 * again — an AgentaOS payment-link id, for instance. {@code name} is display text and may be null: a
 * provider that only ever knew the id is not going to invent a label, and a consumer that needs one has
 * its own catalogue.
 *
 * <p>There is deliberately no price here. A plan's price at the moment a subscription was created and
 * its price today are different facts, and only the events that carry {@link Money} state which one they
 * mean. See {@link net.aetherealtech.payments.event.SubscriptionPlanChanged}, which carries two plans and
 * no money at all.
 *
 * @param ref  the provider's identifier for the plan, never blank
 * @param name display text for the plan, or null when the provider supplied none
 */
public record Plan(String ref, String name) {

    public Plan {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("plan ref must not be blank");
        }
    }

    /** A plan known only by its identifier. */
    public static Plan of(final String ref) {
        return new Plan(ref, null);
    }

    public static Plan of(final String ref, final String name) {
        return new Plan(ref, name);
    }

    /** {@link #name()} when the provider supplied one, otherwise {@link #ref()}. Never null. */
    public String displayName() {
        return Objects.requireNonNullElse(name, ref);
    }
}
