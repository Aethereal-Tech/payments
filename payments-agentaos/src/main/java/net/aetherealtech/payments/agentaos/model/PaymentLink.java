package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Plan;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One payment link, as {@code /api/v1/gateway/payment-links} returns it.
 *
 * <p>A link of {@link LinkType#SUBSCRIPTION} is what this SPI calls a plan: it carries the price and the
 * cadence, and a buyer paying it is the only way a subscription comes into existence here. Its
 * {@link #id()} is what a {@link net.aetherealtech.payments.PaymentIntent#planRef()} names.
 *
 * @param id              the link's identifier — the plan reference; never blank
 * @param orgId           the AgentaOS organization
 * @param amount          the price, in major units, with its currency
 * @param description     what the buyer is paying for
 * @param name            the link's display name, which becomes the subscription's plan name
 * @param imageUrl        an image for the hosted page
 * @param status          whether it still accepts buyers
 * @param sellerMode      how it settles — derived by the server, never sent
 * @param type            one charge or a subscription
 * @param billingInterval the cadence, for a subscription link
 * @param trialPeriodDays a free trial's length in days, or null
 * @param checkoutUrl     the link a buyer opens
 * @param metadata        the merchant's own key/value pairs
 * @param checkoutFields  extra questions the hosted page asks
 * @param webhookUrl      where AgentaOS POSTs events for sessions opened against this link
 * @param successUrl      where the buyer lands after paying
 * @param cancelUrl       where the buyer lands after abandoning
 * @param taxRateId       the tax rate applied
 * @param paymentCount    how many payments this link has taken
 * @param expiresAt       when the link stops accepting buyers
 * @param createdAt       when it was created
 * @param updatedAt       when it last changed
 */
public record PaymentLink(
        String id,
        String orgId,
        Money amount,
        String description,
        String name,
        String imageUrl,
        PaymentLinkStatus status,
        SellerMode sellerMode,
        LinkType type,
        BillingInterval billingInterval,
        Integer trialPeriodDays,
        String checkoutUrl,
        Map<String, String> metadata,
        List<CheckoutField> checkoutFields,
        String webhookUrl,
        String successUrl,
        String cancelUrl,
        String taxRateId,
        Integer paymentCount,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public PaymentLink {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        checkoutFields = checkoutFields == null ? List.of() : List.copyOf(checkoutFields);
    }

    /** One link read from a gateway response. */
    public static PaymentLink from(final Map<String, Object> body) {
        final Long trialDays = Json.integer(body, "trial_period_days");
        final Long payments = Json.integer(body, "payment_count");
        return new PaymentLink(
                Json.string(body, "id"),
                Json.string(body, "org_id"),
                Amounts.fromMajorUnits(Json.decimal(body, "amount"), Json.string(body, "currency")),
                Json.string(body, "description"),
                Json.string(body, "name"),
                Json.string(body, "image_url"),
                PaymentLinkStatus.fromWire(Json.string(body, "status")),
                SellerMode.fromWire(Json.string(body, "seller_mode")),
                LinkType.fromWire(Json.string(body, "type")),
                BillingInterval.fromWire(Json.string(body, "billing_interval")),
                trialDays == null ? null : trialDays.intValue(),
                Json.string(body, "checkout_url"),
                Json.stringMap(body, "metadata"),
                Json.objects(body, "checkout_fields").stream().map(CheckoutField::from).toList(),
                Json.string(body, "webhook_url"),
                Json.string(body, "success_url"),
                Json.string(body, "cancel_url"),
                Json.string(body, "tax_rate_id"),
                payments == null ? null : payments.intValue(),
                Json.instant(body, "expires_at"),
                Json.instant(body, "created_at"),
                Json.instant(body, "updated_at"));
    }

    /** This link as the SPI's plan: its id, and its display name where it has one. */
    public Plan asPlan() {
        return new Plan(id, name);
    }
}
