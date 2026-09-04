package net.aetherealtech.payments.agentaos.model;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import net.aetherealtech.payments.Money;
import net.aetherealtech.payments.Provisional;
import net.aetherealtech.payments.agentaos.internal.Amounts;
import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One checkout session, as {@code /api/v1/gateway/sessions} returns it.
 *
 * <p>Two identifiers, and they are not interchangeable. {@link #id()} is the row's primary key;
 * {@link #sessionId()} is what every later call keys on — retrieve, cancel, and the
 * {@code checkout.session.completed} webhook — and is therefore what this adapter persists as the
 * checkout reference.
 *
 * <p>The response is read as it arrives: snake_case. The SDK camel-cases responses in its HTTP layer, so
 * its TypeScript field names describe what a JavaScript caller sees, not what the server sent.
 *
 * @param id             the session row's own identifier
 * @param paymentLinkId  the link this session was opened against, or null for a link-less session
 * @param orgId          the AgentaOS organization
 * @param sessionId      the handle every later call and the webhook use; never blank
 * @param checkoutUrl    where to send the buyer's browser
 * @param x402Url        the same purchase offered over HTTP 402, for a payer that is not a browser
 * @param status         where the session got to
 * @param sellerMode     how it settles — derived by the server, never sent
 * @param amountOverride what this session charges when it overrides its link's price, else null
 * @param currency       the ISO 4217 code the session is denominated in
 * @param metadata       the merchant's own key/value pairs, echoed back
 * @param successUrl     where the buyer lands after paying
 * @param cancelUrl      where the buyer lands after abandoning
 * @param invoiceId      the invoice raised for this session, once there is one
 * @param invoiceNumber  that invoice's human-readable number
 * @param expiresAt      when an unpaid session stops accepting the buyer
 * @param createdAt      when it was opened
 * @param updatedAt      when it last changed
 */
public record Checkout(
        String id,
        String paymentLinkId,
        String orgId,
        String sessionId,
        String checkoutUrl,
        String x402Url,
        CheckoutStatus status,
        SellerMode sellerMode,
        Money amountOverride,
        String currency,
        Map<String, String> metadata,
        String successUrl,
        String cancelUrl,
        String invoiceId,
        String invoiceNumber,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public Checkout {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** One session read from a gateway response. */
    public static Checkout from(final Map<String, Object> body) {
        final String currency = Json.string(body, "currency");
        return new Checkout(
                Json.string(body, "id"),
                Json.string(body, "payment_link_id"),
                Json.string(body, "org_id"),
                Json.string(body, "session_id"),
                Json.string(body, "checkout_url"),
                Json.string(body, "x402_url"),
                CheckoutStatus.fromWire(Json.string(body, "status")),
                SellerMode.fromWire(Json.string(body, "seller_mode")),
                Amounts.fromMajorUnits(Json.decimal(body, "amount_override"), currency),
                currency,
                Json.stringMap(body, "metadata"),
                Json.string(body, "success_url"),
                Json.string(body, "cancel_url"),
                Json.string(body, "invoice_id"),
                Json.string(body, "invoice_number"),
                Json.instant(body, "expires_at"),
                Json.instant(body, "created_at"),
                Json.instant(body, "updated_at"));
    }

    /**
     * The HTTP 402 entry point, when this session has one.
     *
     * <p>An {@link Optional} rather than the bare field because it is the one part of this record a
     * caller might publish as a link, and a session without one would otherwise publish "null".
     */
    @Provisional("types.ts declares x402Url on Checkout, but a source comment in "
            + "packages/wallet/src/mcp/tools/pay-create-checkout.ts says it \"exists at runtime but may not "
            + "be in the published types yet\" and casts around it — the two disagree about which "
            + "deployments actually return it.")
    public Optional<String> machinePaymentUrl() {
        return Optional.ofNullable(x402Url);
    }
}
