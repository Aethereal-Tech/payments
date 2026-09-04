package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payment instrument details supplied server-to-server.
 *
 * <p>The API models this as a one-of; only SEPA direct debit ({@code ibanData}) is modelled here,
 * because it is the one variant the docs sanction sending without PCI scope. Card data belongs in a
 * {@code transactionToken} from payment.js, never in this object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentData(IbanData ibanData) {

    public static PaymentData ofIban(IbanData ibanData) {
        return new PaymentData(ibanData);
    }
}
