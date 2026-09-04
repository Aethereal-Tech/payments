package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Turns a transaction into a pay-by-link: instead of redirecting a customer who is present, the
 * gateway issues a link that can be paid later.
 *
 * <p>There is no pay-by-link endpoint. This travels embedded in a debit, preauthorize, register or
 * payout, and the resulting link comes back as {@link PayByLinkData}.
 *
 * @param expirationInMinute how long the link stays payable; the field is singular on the wire
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayByLink(Boolean sendByEmail, Integer expirationInMinute) {

    public PayByLink {
        if (expirationInMinute != null && expirationInMinute < 1) {
            throw new IllegalArgumentException("expirationInMinute must be at least 1");
        }
    }

    /** A link the merchant delivers itself. */
    public static PayByLink withoutEmail(Integer expirationInMinute) {
        return new PayByLink(false, expirationInMinute);
    }

    /** A link the gateway emails to the customer on the transaction. */
    public static PayByLink byEmail(Integer expirationInMinute) {
        return new PayByLink(true, expirationInMinute);
    }
}
