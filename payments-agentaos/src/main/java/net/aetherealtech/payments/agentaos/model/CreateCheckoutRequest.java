package net.aetherealtech.payments.agentaos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.aetherealtech.payments.Money;

/**
 * What to POST to {@code /api/v1/gateway/sessions} to open a checkout.
 *
 * <p>Either {@link #linkId()} or {@link #amount()} carries the price: a session opened against a payment
 * link inherits everything the link states, a link-less one states its own amount and currency.
 *
 * <p><strong>{@code sellerMode} is never sent.</strong> It is derived by the server — inherited from the
 * link, or resolved from the merchant's account — and the SDK's own tests assert it is absent from the
 * body. Sending it is how a client meets a 400 that says nothing useful.
 *
 * @param linkId           the payment link to open a session against, or null
 * @param amount           the price for a link-less session, or null when the link carries it
 * @param description      what the buyer is paying for
 * @param taxRateId        the organization's tax rate to apply
 * @param buyerEmail       the buyer's email, prefilled on the hosted page
 * @param buyerName        the buyer's name
 * @param buyerCompany     the buyer's company, for a business sale
 * @param buyerCountry     ISO 3166-1 alpha-2, which is what decides the tax treatment
 * @param buyerAddress     the buyer's address as one string
 * @param buyerVat         the buyer's VAT identification number
 * @param amountOverride   a price for this session that differs from its link's, or null
 * @param metadata         key/value pairs echoed back on the session and its webhook
 * @param webhookUrl       where AgentaOS POSTs the event for THIS session
 * @param successUrl       where the buyer lands after paying
 * @param cancelUrl        where the buyer lands after abandoning
 * @param expiresIn        how long the session stays open, in seconds, 300–86400; null takes the default
 * @param supportedNetworks CAIP-2 chain identifiers to accept, e.g. {@code eip155:8453}; empty takes the default
 * @param dueDate          the invoice due date, a bare {@code YYYY-MM-DD}
 */
public record CreateCheckoutRequest(
        String linkId,
        Money amount,
        String description,
        String taxRateId,
        String buyerEmail,
        String buyerName,
        String buyerCompany,
        String buyerCountry,
        String buyerAddress,
        String buyerVat,
        BigDecimal amountOverride,
        Map<String, String> metadata,
        String webhookUrl,
        String successUrl,
        String cancelUrl,
        Integer expiresIn,
        List<String> supportedNetworks,
        LocalDate dueDate) {

    /** The narrowest window AgentaOS accepts for {@link #expiresIn()}. */
    public static final int MIN_EXPIRES_IN_SECONDS = 300;

    /** The widest window AgentaOS accepts for {@link #expiresIn()}. */
    public static final int MAX_EXPIRES_IN_SECONDS = 86_400;

    public CreateCheckoutRequest {
        if ((linkId == null || linkId.isBlank()) && amount == null) {
            throw new IllegalArgumentException("a checkout needs either a linkId or an amount");
        }
        if (expiresIn != null && (expiresIn < MIN_EXPIRES_IN_SECONDS || expiresIn > MAX_EXPIRES_IN_SECONDS)) {
            throw new IllegalArgumentException("expiresIn must be between " + MIN_EXPIRES_IN_SECONDS
                    + " and " + MAX_EXPIRES_IN_SECONDS + " seconds, got " + expiresIn);
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        supportedNetworks = supportedNetworks == null ? List.of() : List.copyOf(supportedNetworks);
    }

    /** A one-off charge with no payment link behind it. */
    public static Builder of(final Money amount) {
        return new Builder(null, amount);
    }

    /** A session against an existing payment link, which carries the price and the cadence. */
    public static Builder forLink(final String linkId) {
        return new Builder(linkId, null);
    }

    /**
     * The request body's fields, camelCase and in a fixed order.
     *
     * <p>Request bodies are NOT transformed — the server's own DTOs are camelCase, which is the exact
     * opposite of its snake_case responses. That asymmetry is real and is the easiest thing here to get
     * wrong in either direction.
     */
    public Map<String, Object> toWire() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("linkId", linkId);
        out.put("amount", amount == null ? null : amount.amount());
        out.put("currency", amount == null ? null : amount.currency());
        out.put("description", description);
        out.put("taxRateId", taxRateId);
        out.put("buyerEmail", buyerEmail);
        out.put("buyerName", buyerName);
        out.put("buyerCompany", buyerCompany);
        out.put("buyerCountry", buyerCountry);
        out.put("buyerAddress", buyerAddress);
        out.put("buyerVat", buyerVat);
        out.put("amountOverride", amountOverride);
        out.put("metadata", metadata.isEmpty() ? null : metadata);
        out.put("webhookUrl", webhookUrl);
        out.put("successUrl", successUrl);
        out.put("cancelUrl", cancelUrl);
        out.put("expiresIn", expiresIn);
        out.put("supportedNetworks", supportedNetworks.isEmpty() ? null : supportedNetworks);
        out.put("dueDate", dueDate == null ? null : dueDate.toString());
        return out;
    }

    /** Builds a {@link CreateCheckoutRequest}; eighteen optional parameters is not a constructor. */
    public static final class Builder {

        private final String linkId;
        private final Money amount;
        private String description;
        private String taxRateId;
        private String buyerEmail;
        private String buyerName;
        private String buyerCompany;
        private String buyerCountry;
        private String buyerAddress;
        private String buyerVat;
        private BigDecimal amountOverride;
        private Map<String, String> metadata;
        private String webhookUrl;
        private String successUrl;
        private String cancelUrl;
        private Integer expiresIn;
        private List<String> supportedNetworks;
        private LocalDate dueDate;

        private Builder(final String linkId, final Money amount) {
            this.linkId = linkId;
            this.amount = amount;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder taxRateId(final String taxRateId) {
            this.taxRateId = taxRateId;
            return this;
        }

        /** Everything AgentaOS accepts about the buyer, which is what its invoices are built from. */
        public Builder buyer(final String email, final String name, final String country) {
            this.buyerEmail = email;
            this.buyerName = name;
            this.buyerCountry = country;
            return this;
        }

        public Builder buyerEmail(final String buyerEmail) {
            this.buyerEmail = buyerEmail;
            return this;
        }

        public Builder buyerName(final String buyerName) {
            this.buyerName = buyerName;
            return this;
        }

        public Builder buyerCompany(final String buyerCompany) {
            this.buyerCompany = buyerCompany;
            return this;
        }

        public Builder buyerCountry(final String buyerCountry) {
            this.buyerCountry = buyerCountry;
            return this;
        }

        public Builder buyerAddress(final String buyerAddress) {
            this.buyerAddress = buyerAddress;
            return this;
        }

        public Builder buyerVat(final String buyerVat) {
            this.buyerVat = buyerVat;
            return this;
        }

        public Builder amountOverride(final BigDecimal amountOverride) {
            this.amountOverride = amountOverride;
            return this;
        }

        public Builder metadata(final Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder webhookUrl(final String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        /** Where the buyer's browser lands, either way. */
        public Builder urls(final String successUrl, final String cancelUrl) {
            this.successUrl = successUrl;
            this.cancelUrl = cancelUrl;
            return this;
        }

        public Builder expiresIn(final Integer expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder supportedNetworks(final List<String> supportedNetworks) {
            this.supportedNetworks = supportedNetworks;
            return this;
        }

        public Builder dueDate(final LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public CreateCheckoutRequest build() {
            return new CreateCheckoutRequest(linkId, amount, description, taxRateId, buyerEmail, buyerName,
                    buyerCompany, buyerCountry, buyerAddress, buyerVat, amountOverride, metadata, webhookUrl,
                    successUrl, cancelUrl, expiresIn, supportedNetworks, dueDate);
        }
    }
}
