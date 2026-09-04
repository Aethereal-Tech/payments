package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.Map;

import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One buyer who has paid this organization, as {@code /api/v1/gateway/customers} lists it.
 *
 * <p>Read-only and list-only: AgentaOS creates these itself from completed checkouts, and there is no
 * endpoint that makes, changes or retrieves one.
 *
 * @param id                the customer's identifier
 * @param email             their email address, which is what a subscription record names them by
 * @param name              their name
 * @param country           ISO 3166-1 alpha-2
 * @param vatNumber         their VAT identification number, for a business buyer
 * @param stripeCustomerId  the underlying Stripe customer, for a support conversation
 * @param createdAt         when they first paid
 */
public record Customer(
        String id,
        String email,
        String name,
        String country,
        String vatNumber,
        String stripeCustomerId,
        Instant createdAt) {

    /** One customer read from a gateway response. */
    public static Customer from(final Map<String, Object> body) {
        return new Customer(
                Json.string(body, "id"),
                Json.string(body, "email"),
                Json.string(body, "name"),
                Json.string(body, "country"),
                Json.string(body, "vat_number"),
                Json.string(body, "stripe_customer_id"),
                Json.instant(body, "created_at"));
    }

    /** This buyer in the SPI's vocabulary, for an intent that reuses what AgentaOS already knows. */
    public net.aetherealtech.payments.Customer toSpi() {
        return new net.aetherealtech.payments.Customer(id, email, name, country, vatNumber);
    }
}
